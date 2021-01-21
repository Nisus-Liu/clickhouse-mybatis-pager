package com.foo;

import com.baomidou.mybatisplus.core.MybatisDefaultParameterHandler;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.sqkb.bigdata.common.bean.vo.MyPage;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 针对 clickhouse 等 mybatis plus 不支持的的mybatis分页拦截器
 *
 * mybatis-plus 不支持clickhouse的分页sql处理
 *
 * 参照: com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor
 *
 * @author dafei
 * @version 0.1
 * @date 2019/11/22 12:14
 */
@Slf4j
@Setter
@Component
@Accessors(chain = true)
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class MyPaginationInterceptor implements Interceptor {

    public static final Set<String> SUPPORT_DIALECTTYPES = new HashSet<String>(){{
        add("clickhouse");
    }};
    /**
     * 单页限制 500 条，小于 0 如 -1 不受限制
     */
    private final int limit = 500;

    private String dialectType;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long st = System.currentTimeMillis();
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        //通过MetaObject优雅访问对象的属性，这里是访问statementHandler的属性;：MetaObject是Mybatis提供的一个用于方便、
        //优雅访问对象属性的对象，通过它可以简化代码、不需要try/catch各种reflect异常，同时它支持对JavaBean、Collection、Map三种类型对象的操作。
        MetaObject metaObject = MetaObject
                .forObject(statementHandler, SystemMetaObject.DEFAULT_OBJECT_FACTORY, SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY,
                        new DefaultReflectorFactory());
        //先拦截到RoutingStatementHandler，里面有个StatementHandler类型的delegate变量，其实现类是BaseStatementHandler，然后就到BaseStatementHandler的成员变量mappedStatement
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        //id为执行的mapper方法的全路径名，如com.uv.dao.UserMapper.insertUser
        String id = mappedStatement.getId();
        //sql语句类型 select、delete、insert、update
        // String sqlCommandType = mappedStatement.getSqlCommandType().toString();
        if (!isSupported(mappedStatement)) {
            return invocation.proceed();
        }



        //数据库连接信息
//        Configuration configuration = mappedStatement.getConfiguration();
//        ComboPooledDataSource dataSource = (ComboPooledDataSource)configuration.getEnvironment().getDataSource();
//        dataSource.getJdbcUrl();

        BoundSql boundSql = statementHandler.getBoundSql();
        Object paramObj = boundSql.getParameterObject();
        // 判断参数里是否有page对象
        MyPage<?> page = null;
        if (paramObj instanceof MyPage) {
            page = (MyPage<?>) paramObj;
        } else if (paramObj instanceof Map) {
            for (Object arg : ((Map<?, ?>) paramObj).values()) {
                if (arg instanceof MyPage) {
                    page = (MyPage<?>) arg;
                    break;
                }
            }
        }

        /*
         * 不需要分页的场合，如果 size 小于 0 返回结果集
         */
        if (null == page || page.getPageSize() < 0) {
            return invocation.proceed();
        }

        log.debug("----sqlId----"+id);


        /*
         * 处理单页条数限制
         */
        if (limit <= page.getPageSize()) {
            page.setPageSize(limit);
        }

        //获取到原始sql语句
        String originalSql = boundSql.getSql();
        Connection connection = (Connection) invocation.getArgs()[0];

        // 查询总记录数
        if (page.isSearchCount()) {
            String countSql = getCountSql(originalSql);
            long t0 = System.currentTimeMillis();
            this.queryTotal(countSql, mappedStatement, boundSql, connection, page);
            log.debug("get total, cost time: {}ms", System.currentTimeMillis() - t0);
            if (page.getTotal() <= 0) {
                return null;
            }
        }


        // 分页查询
        //通过反射修改sql语句
        String pageSql = this.buildPaginationSql(originalSql, page);
        Field field = boundSql.getClass().getDeclaredField("sql");
        field.setAccessible(true);
        field.set(boundSql, pageSql);
        log.debug("original sql: {}, after paging: {}", originalSql, pageSql);

        long t0 = System.currentTimeMillis();
        Object ret = invocation.proceed();
        log.debug("page query, cost time: {}ms", System.currentTimeMillis() - t0);
        log.debug("total cost time: {}ms", System.currentTimeMillis() - st);
        return ret;
    }

    @Override
    public Object plugin(Object o) {
        return Plugin.wrap(o, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // ? 没走这里 ?
        String dialectType = properties.getProperty("dialectType");
        log.debug("dialectType: {}",dialectType);
        if (StringUtils.isNotEmpty(dialectType)) {
            this.dialectType = dialectType;
        }
    }

    private boolean isSupported(MappedStatement mappedStatement) {
        if (SqlCommandType.SELECT != mappedStatement.getSqlCommandType()
                || StatementType.CALLABLE == mappedStatement.getStatementType()
                /*|| !SUPPORT_DIALECTTYPES.contains(dialectType)*/) {
            return false;
        }
        return true;
    }


    private String buildPaginationSql(String originalSql, MyPage<?> page) {
        String sql = originalSql + " LIMIT " + page.getStartPosition() + StringPool.COMMA + page.getPageSize();
        return sql;
    }

    private String getCountSql(String originalSql) {
        return String.format("SELECT COUNT(1) FROM ( %s ) TOTAL", originalSql);
    }

    private void queryTotal(String sql, MappedStatement mappedStatement, BoundSql boundSql, Connection connection, MyPage<?> page) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // 借助 mybatis plus 帮我设置参数
            DefaultParameterHandler parameterHandler = new MybatisDefaultParameterHandler(mappedStatement, boundSql.getParameterObject(), boundSql);
            parameterHandler.setParameters(statement);
            long total = 0;
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    total = resultSet.getLong(1);
                }
            }
            page.setTotal(total);
            // /*
            //  * 溢出总页数，设置第一页
            //  */
            // long pages = page.getPages();
            // if (overflowCurrent && page.getCurrent() > pages) {
            //     // 设置为第一条
            //     page.setCurrent(1);
            // }

        } catch (Exception e) {
            throw new RuntimeException("ClickhousePaginationInterceptor#queryTotal error, sql: "+sql, e);
        }
    }
}
