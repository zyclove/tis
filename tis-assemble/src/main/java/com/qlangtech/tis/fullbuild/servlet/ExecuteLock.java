/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.qlangtech.tis.fullbuild.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-09-01 09:50
 **/
public class ExecuteLock {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteLock.class);
    private final Queue<TaskFuture<?>> futureQueue = new ConcurrentLinkedQueue<>();

    // private final ReentrantLock lock;
    private final AtomicBoolean lock = new AtomicBoolean(false);

    // 开始时间，需要用它判断是否超时
    private AtomicLong startTimestamp;

    // 超时时间为9个小时
    private static final long EXPIR_TIME = 1000 * 60 * 60 * 9;

    private final String taskOwnerUniqueName;

    public ExecuteLock(String indexName) {
        this.taskOwnerUniqueName = indexName;
        // 这个lock 的问题是必须要由拥有这个lock的owner thread 来释放锁，不然的话就会抛异常
        // this.lock = new ReentrantLock();
        this.startTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    public boolean matchTask(int taskId) {
        for (TaskFuture<?> f : futureQueue) {
            if (f.taskId == taskId) {
                return true;
            }
        }
        return false;
    }

    public void cancelAllFuture() {
        for (TaskFuture<?> f : this.futureQueue) {
            f.future.cancel(true);
        }
    }


    public void addTaskFuture(Integer taskId, Future<?> future) {
        this.futureQueue.add(new TaskFuture(taskId, future));
    }

    public String getTaskOwnerUniqueName() {
        return taskOwnerUniqueName;
    }

    boolean isExpire() {
        long start = startTimestamp.get();
        long now = System.currentTimeMillis();
        // 没有完成
        // 查看是否超时
        boolean expire = ((start + EXPIR_TIME) < now);
        if (expire) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            logger.info("time:" + format.format(new Date(start)) + "is expire");
        }
        return expire;
    }

    /**
     * 尝试加锁
     *
     * @return
     */
    public boolean lock() {
        if (this.lock.compareAndSet(false, true)) {
            this.startTimestamp.getAndSet(System.currentTimeMillis());
            return true;
        } else {
            return false;
        }
    }

    /**
     * 释放锁
     */
    public void unlock() {
        // this.lock.unlock();
        this.lock.lazySet(false);
    }

    public void clearLockFutureQueue() {
        synchronized (TisServlet.class) {
            this.unlock();
            this.futureQueue.clear();
            TisServlet.idles.remove(this.taskOwnerUniqueName, this);
        }
    }


    private static class TaskFuture<T> {
        private final Integer taskId;
        private final Future<T> future;

        public TaskFuture(Integer taskId, Future<T> future) {
            this.taskId = taskId;
            this.future = future;
        }
    }
}
