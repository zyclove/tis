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
package com.qlangtech.tis.flume;

import com.qlangtech.tis.manage.common.TISCollectionUtils;
import com.qlangtech.tis.manage.common.TisUTF8;
import com.qlangtech.tis.order.center.IParamContext;
import org.apache.commons.lang.StringUtils;
import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @date 2016年4月15日 下午3:20:33
 */
public class TisIncrLoggerSink extends AbstractSink implements Configurable {

    private static final Logger logger = LoggerFactory.getLogger(TisIncrLoggerSink.class);

    private static final Map<String, Logger> loggers = new HashMap<>();

    public Logger getLogger(String name) {
        Logger logger = loggers.get(name);
        if (logger == null) {
            logger = LoggerFactory.getLogger(name);
            loggers.put(name, logger);
        }
        return logger;
    }

    @Override
    public void configure(Context context) {
        // String myProp = context.getString("myProp", "defaultValue");
        // Process the myProp value (e.g. validation)
        // Store myProp for later retrieval by process() method
        // this.myProp = myProp;
    }

    @Override
    public void start() {
        // Initialize the connection to the external repository (e.g. HDFS) that
        // this Sink will forward Events to ..
    }

    @Override
    public void stop() {
        // Disconnect from the external respository and do any
        // additional cleanup (e.g. releasing resources or nulling-out
        // field values) ..
    }

    @Override
    public Status process() throws EventDeliveryException {
        // logger.info("start process");
        Status status = null;
        // Start transaction
        Channel ch = getChannel();
        Transaction txn = ch.getTransaction();
        txn.begin();
        try {
            int txnEventCount = 0;
            for (txnEventCount = 0; txnEventCount < 200; txnEventCount++) {
                try {
                    Event event = ch.take();
                    if (event == null) {
                        break;
                    }
                    Map<String, String> headers = event.getHeaders();
                    String execGroup = headers.get("incr_exec_group");
                    String application = headers.get("application");
                    String host = headers.get("host");
                    MDC.put("application", application);
                    MDC.put("group", execGroup);
                    MDC.put("host", host);
                    MDC.put(TISCollectionUtils.KEY_COLLECTION, headers.get(TISCollectionUtils.KEY_COLLECTION));
                    Object taskid = headers.get(IParamContext.KEY_TASK_ID);
                    if (taskid != null) {
                        MDC.put(IParamContext.KEY_TASK_ID, String.valueOf(taskid));
                    }
                    String logtype = headers.get("logtype");
                    if (StringUtils.isEmpty(logtype)) {
                        logger.info(new String(event.getBody(), TisUTF8.get()));
                    } else {
                        getLogger(logtype).info(new String(event.getBody(), "utf8"));
                    }
                } finally {
                    MDC.clear();
                }
                // logger.info("take an event {}", event);
                // System.out.println(new String(event.getBody(), "utf8"));
                // for (Map.Entry<String, String> entry : event.getHeaders()
                // .entrySet()) {
                // System.out.println(entry.getKey() + ":" + entry.getValue());
                // }
                // System.out
                // .println("==========================================");
                // Send the Event to the external repository.
                // storeSomeData(e);
            }
            txn.commit();
            if (txnEventCount < 1) {
                return Status.BACKOFF;
            } else {
                return Status.READY;
            }
            // status = Status.READY;
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            txn.rollback();
            status = Status.BACKOFF;
            // re-throw all Errors
            if (t instanceof Error) {
                throw (Error) t;
            }
        } finally {
            txn.close();
            MDC.clear();
        }
        return status;
    }
}
