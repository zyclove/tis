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

package com.qlangtech.tis.plugin.ds;

import com.alibaba.citrus.turbine.Context;
import com.google.common.collect.Lists;
import com.qlangtech.tis.db.parser.DBConfigParser;
import com.qlangtech.tis.lang.TisException;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-06-06 19:48
 **/
public abstract class BasicDataSourceFactory extends DataSourceFactory implements JdbcUrlBuilder {
    private static final Logger logger = LoggerFactory.getLogger(BasicDataSourceFactory.class);

    @FormField(identity = true, ordinal = 0, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.identity})
    public String name;

    // 数据库名称
    @FormField(ordinal = 1, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.identity})
    public String dbName;

    @FormField(ordinal = 2, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.identity})
    public String userName;

    @FormField(ordinal = 3, type = FormFieldType.PASSWORD, validate = {})
    public String password;
    /**
     * 节点描述
     */
    @FormField(ordinal = 5, type = FormFieldType.TEXTAREA, validate = {Validator.require})
    public String nodeDesc;

    @FormField(ordinal = 7, type = FormFieldType.INT_NUMBER, validate = {Validator.require, Validator.integer})
    public int port;
    /**
     * 数据库编码
     */
    @FormField(ordinal = 9, type = FormFieldType.ENUM, validate = {Validator.require, Validator.identity})
    public String encode;
    /**
     * 附加参数
     */
    @FormField(ordinal = 11, type = FormFieldType.INPUTTEXT)
    public String extraParams;


    public String getUserName() {
        return this.userName;
    }

    public String getPassword() {
        return this.password;
    }


    @Override
    public List<ColumnMetaData> getTableMetadata(final String table) {
        if (StringUtils.isBlank(table)) {
            throw new IllegalArgumentException("param table can not be null");
        }
        List<ColumnMetaData> columns = new ArrayList<>();
        try {

            final DBConfig dbConfig = getDbConfig();
            dbConfig.vistDbName((config, ip, dbname) -> {
                columns.addAll(parseTableColMeta(table, config, ip, dbname));
                return true;
            });
            return columns;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<ColumnMetaData> parseTableColMeta(String table, DBConfig config, String ip, String dbname) throws Exception {
        // List<ColumnMetaData> columns = Lists.newArrayList();
        String jdbcUrl = buidJdbcUrl(config, ip, dbname);

        return parseTableColMeta(table, jdbcUrl);
    }


    /**
     * 访问第一个JDBC Connection对象
     *
     * @param connProcessor
     */
    public final void visitFirstConnection(final IConnProcessor connProcessor) {
        this.visitConnection(connProcessor, false);
    }

    /**
     * 遍历所有conn
     *
     * @param connProcessor
     */
    public final void visitAllConnection(final IConnProcessor connProcessor) {
        this.visitConnection(connProcessor, true);
    }

    private final void visitConnection(final IConnProcessor connProcessor, final boolean visitAll) {
        try {
            final DBConfig dbConfig = getDbConfig();
            dbConfig.vistDbName((config, ip, databaseName) -> {
                visitConnection(config, ip, databaseName, connProcessor);
                return !visitAll;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public final List<String> getTablesInDB() {
        try {
            final List<String> tabs = new ArrayList<>();

            this.visitFirstConnection((conn) -> {
                refectTableInDB(tabs, conn);
            });

//            final DBConfig dbConfig = getDbConfig();
//            dbConfig.vistDbName((config, ip, databaseName) -> {
//                visitConnection(config, ip, databaseName, (conn) -> {
//                    refectTableInDB(tabs, conn);
//                });
//                return true;
//            });
            return tabs;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Connection getConnection(String jdbcUrl) throws SQLException {
        return super.getConnection(jdbcUrl);
    }

    public void refectTableInDB(List<String> tabs, Connection conn) throws SQLException {
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.execute(getRefectTablesSql());
            resultSet = statement.getResultSet();
            while (resultSet.next()) {
                tabs.add(resultSet.getString(1));
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
        }
    }

    protected String getRefectTablesSql() {
        return "show tables";
    }

    public final DBConfig getDbConfig() {
        final DBConfig dbConfig = new DBConfig(this);
        dbConfig.setName(this.dbName);
        dbConfig.setDbEnum(DBConfigParser.parseDBEnum(dbName, this.nodeDesc));
        return dbConfig;
    }


    public List<String> getJdbcUrls() {
        final DBConfig dbLinkMetaData = this.getDbConfig();
        List<String> jdbcUrls = Lists.newArrayList();
        dbLinkMetaData.vistDbURL(true, (dbName, jdbcUrl) -> {
            jdbcUrls.add(jdbcUrl);
        }, false);
        return jdbcUrls;
    }

    @Override
    public DataDumpers getDataDumpers(TISTable table) {
        if (table == null) {
            throw new IllegalArgumentException("param table can not be null");
        }
        List<String> jdbcUrls = getJdbcUrls();

        return DataDumpers.create(jdbcUrls, table); // new DataDumpers(length, dsIt);
    }


    private void visitConnection(DBConfig db, String ip, String dbName
            , IConnProcessor p) throws Exception {
        if (db == null) {
            throw new IllegalStateException("param db can not be null");
        }
        if (StringUtils.isEmpty(ip)) {
            throw new IllegalArgumentException("param ip can not be null");
        }
        if (StringUtils.isEmpty(dbName)) {
            throw new IllegalArgumentException("param dbName can not be null");
        }

//        if (StringUtils.isEmpty(password)) {
//            throw new IllegalArgumentException("param password can not be null");
//        }
        if (p == null) {
            throw new IllegalArgumentException("param IConnProcessor can not be null");
        }
        //Connection conn = null;
        String jdbcUrl = buidJdbcUrl(db, ip, dbName);
        try {
            validateConnection(jdbcUrl, p);
        } catch (Exception e) {
            //MethodHandles.lookup().lookupClass()
            throw new TisException("请确认插件:" + this.getClass().getSimpleName() + "配置:" + this.identityValue() + ",jdbcUrl:" + jdbcUrl, e);
        }
    }


    @Override
    protected Class<BasicRdbmsDataSourceFactoryDescriptor> getExpectDesClass() {
        return BasicRdbmsDataSourceFactoryDescriptor.class;
    }

    public interface IConnProcessor {
        void vist(Connection conn) throws SQLException;
    }

    public abstract static class BasicRdbmsDataSourceFactoryDescriptor extends BaseDataSourceFactoryDescriptor<BasicDataSourceFactory> {
        private static final Pattern urlParamsPattern = Pattern.compile("(\\w+?\\=\\w+?)(\\&\\w+?\\=\\w+?)*");

        public boolean validateExtraParams(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {

            Matcher matcher = urlParamsPattern.matcher(value);
            if (!matcher.matches()) {
                msgHandler.addFieldError(context, fieldName, "不符合格式：" + urlParamsPattern);
                return false;
            }
            return true;
        }
    }

    public static void main(String[] args) {
        Matcher matcher = BasicRdbmsDataSourceFactoryDescriptor.urlParamsPattern.matcher("kkk=lll&bbb=lll");
        System.out.println(matcher.matches());
    }

}
