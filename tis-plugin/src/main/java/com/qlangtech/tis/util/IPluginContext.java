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
package com.qlangtech.tis.util;

import com.alibaba.citrus.turbine.Context;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.plugin.ds.DataSourceFactory;
import com.qlangtech.tis.runtime.module.misc.IMessageHandler;
import org.apache.commons.lang.StringUtils;


/**
 * @author 百岁（baisui@qlangtech.com）
 * @date 2020/04/13
 */
public interface IPluginContext extends IMessageHandler {

    public static IPluginContext namedContext(String collectionName) {
        if (StringUtils.isEmpty(collectionName)) {
            throw new IllegalArgumentException("param collectionName can not be empty");
        }
        return new IPluginContext() {

            @Override
            public String getExecId() {
                return null;
            }

            @Override
            public boolean isCollectionAware() {
                return true;
            }

            @Override
            public boolean isDataSourceAware() {
                return false;
            }

            @Override
            public void addDb(Descriptor.ParseDescribable<DataSourceFactory> dbDesc, String dbName, Context context,
                              boolean shallUpdateDB) {

            }

            @Override
            public String getCollectionName() {
                return collectionName;
            }

            @Override
            public void errorsPageShow(Context context) {

            }

            @Override
            public void addActionMessage(Context context, String msg) {

            }

            @Override
            public void setBizResult(Context context, Object result) {

            }

            @Override
            public void addErrorMessage(Context context, String msg) {

            }
        };
    }

    /**
     * 执行更新流程客户端会保存一个ExecId的UUID
     *
     * @return
     */
    String getExecId();

    /**
     * 是否在索引
     *
     * @return
     */
    boolean isCollectionAware();

    /**
     * 是否和数据源相关
     *
     * @return
     */
    boolean isDataSourceAware();


    /**
     * TIS default implements: PluginAction.addDb()
     * 向数据库中新添加一条db的记录
     *
     * @param dbName
     * @param context
     */
    void addDb(Descriptor.ParseDescribable<DataSourceFactory> dbDesc, String dbName, Context context, boolean shallUpdateDB);

    String getCollectionName();
}
