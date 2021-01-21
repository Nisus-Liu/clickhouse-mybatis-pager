package com.foo;

import com.baomidou.mybatisplus.core.metadata.OrderItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 定制的分页对象
 *
 * 页号从 1 开始计.
 * 记录起始位置以 0 开始计.
 * @see {@code cn.hutool.db.Page}
 * @author dafei
 * @version 0.1
 * @date 2019/11/22 13:28
 */
public class MyPage<T> {

    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 页码
     */
    private int pageNumber;
    /**
     * 每页结果数
     */
    private int pageSize;
    /**
     * 排序
     */
    private List<OrderItem> orders;

    /**
     * 是否进行 count 查询
     */
    private boolean isSearchCount = true;

    /**
     * 查询数据列表
     */
    private List<T> records = Collections.emptyList();

    /**
     * 总数
     */
    private long total = 0;

    // ---------------------------------------------------------- Constructor start

    public MyPage() {}

    /**
     * 构造
     *
     * @param pageNumber 页码
     * @param pageSize   每页结果数
     */
    public MyPage(int pageNumber, int pageSize) {
        this(pageNumber, pageSize, true);
    }

    public MyPage(int pageNumber, int pageSize, boolean isSearchCount) {
        this.pageNumber = pageNumber < 0 ? 0 : pageNumber;
        this.pageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;
        this.isSearchCount = isSearchCount;
    }

    /**
     * 构造
     *
     * @param pageNumber 页码
     * @param numPerPage 每页结果数
     * @param order      排序对象
     */
    public MyPage(int pageNumber, int numPerPage, OrderItem order) {
        this(pageNumber, numPerPage);
        if (this.orders == null) {
            this.orders = new ArrayList<OrderItem>();
        }
        this.orders.add(order);
    }
    // ---------------------------------------------------------- Constructor start

    // ---------------------------------------------------------- Getters and Setters start

    /**
     * @return 页码
     */
    public int getPageNumber() {
        return pageNumber;
    }

    /**
     * 设置页码
     *
     * @param pageNumber 页码
     * @return
     */
    public MyPage<T> setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber < 0 ? 0 : pageNumber;
        return this;
    }

    /**
     * @return 每页结果数
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页结果数
     *
     * @param pageSize 每页结果数
     * @return
     */
    public MyPage<T> setPageSize(int pageSize) {
        this.pageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;
        return this;
    }

    public long getTotal() {
        return this.total;
    }

    public MyPage<T> setTotal(long total) {
        this.total = total;
        return this;
    }

    public MyPage<T> setRecords(List<T> records) {
        this.records = records;
        return this;
    }

    public List<T> getRecords() {
        return this.records;
    }


    /**
     * @return 排序
     */
    public List<OrderItem> getOrders() {
        return this.orders;
    }

    /**
     * 设置排序
     *
     * @param orders 排序
     * @return
     */
    public MyPage<T> setOrder(OrderItem... orders) {
        this.orders = Arrays.asList(orders);
        return this;
    }

    /**
     * 设置排序
     *
     * @param orders 排序
     */
    public void addOrder(OrderItem... orders) {
        if (null != this.orders) {
            this.orders.addAll(Arrays.asList(orders));
        }
        this.orders = Arrays.asList(orders);
    }

    public boolean isSearchCount() {
        if (total < 0) {
            return false;
        }
        return isSearchCount;
    }

    public MyPage<T> setSearchCount(boolean isSearchCount) {
        this.isSearchCount = isSearchCount;
        return this;
    }

    // ---------------------------------------------------------- Getters and Setters end

    /**
     * @return 开始位置
     */
    public int getStartPosition() {
        return getStartEnd()[0];
    }

    /**
     * @return 结束位置
     */
    public int getEndPosition() {
        return getStartEnd()[1];
    }

    /**
     * 开始位置和结束位置<br>
     * 例如：<br>
     * 页码：1，每页10 =》 [0, 10]<br>
     * 页码：2，每页10 =》 [10, 20]<br>
     * 。。。<br>
     *
     * @return 第一个数为开始位置，第二个数为结束位置
     */
    public int[] getStartEnd() {
        return MyPage.transToStartEnd(pageNumber, pageSize);
    }

    @Override
    public String toString() {
        return "Page [page=" + pageNumber + ", pageSize=" + pageSize + ", order=" + orders +
                ", total=" + total + ", records.size=" + (records==null?null:records.size()) +
                "]";
    }


    /**
     * 将页数和每页条目数转换为开始位置和结束位置<br>
     * 此方法用于不包括结束位置的分页方法<br>
     * 例如：
     *
     * <pre>
     * 页码：1，每页10 =》 [0, 10]
     * 页码：2，每页10 =》 [10, 20]
     * ……
     * </pre>
     *
     * @param pageNo 页码（从1计数）
     * @param pageSize 每页条目数
     * @return 第一个数为开始位置，第二个数为结束位置
     */
    public static int[] transToStartEnd(int pageNo, int pageSize) {
        if (pageNo < 1) {
            pageNo = 1;
        }

        if (pageSize < 1) {
            pageSize = 0;
        }

        final int start = (pageNo - 1) * pageSize;
        if (pageSize < 1) {
            pageSize = 0;
        }
        final int end = start + pageSize;

        return new int[] { start, end };
    }
}
