# clickhouse-mybatis-pager

clickhouse + mybatis-plus 时的分页拦截器.

> `mybatis-plus`(v3.1.2, 以下简称`MP`) 
> 使用分页时, 貌似不支持.
> 本仓库提供使用MP时分页的解决方案. _暂只有关键的文件, 不是完整的可运行的项目._


- `MyPaginationInterceptor` mybatis 自定义分页拦截器. 用于拦截mapper方法, 处理分页逻辑.
- `MyPage` 配合使用的分页对象. 自定义分页拦截器发现mapper方法中有个对象, 就会进入分页处理逻辑.

Mapper 方法中带上 `MyPage` 参数, 就会被自定义分页拦截器拦截并处理分页逻辑. 
```
// FooMapper
List<FooEntity> queryPageList(MyPage<FooEntity> page,
                                                     @Param("params") T otherParams );
```
