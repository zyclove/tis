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
package com.qlangtech.tis.datax;

import com.qlangtech.tis.plugin.ds.ISelectedTab;
import com.qlangtech.tis.util.IPluginContext;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Datax 执行器可以在各种容器上执行 https://github.com/alibaba/DataX
 *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2021-04-07 14:38
 */
public interface IDataxProcessor {
    public String DATAX_CREATE_DDL_FILE_NAME_SUFFIX = ".sql";

    IDataxReader getReader(IPluginContext pluginCtx);

    IDataxWriter getWriter(IPluginContext pluginCtx);

    IDataxGlobalCfg getDataXGlobalCfg();

    public File getDataxCfgDir(IPluginContext pluginCtx);

    /**
     * 取得自动建表目录
     *
     * @param pluginContext
     * @return
     */
    public File getDataxCreateDDLDir(IPluginContext pluginContext);

    /**
     * 更新自动建表的脚本内容
     *
     * @param pluginCtx
     * @param createDDL
     * @param sqlFileName
     * @param overWrite
     */
    public void saveCreateTableDDL(IPluginContext pluginCtx, StringBuffer createDDL, String sqlFileName, boolean overWrite) throws IOException;

    public File getDataXWorkDir(IPluginContext pluginContext);

    /**
     * 从非结构化的数据源导入到结构化的数据源，例如从OSS导入到MySQL
     *
     * @return
     */
    public boolean isReaderUnStructed(IPluginContext pluginCtx);

    public boolean isRDBMS2UnStructed(IPluginContext pluginCtx);

    public boolean isRDBMS2RDBMS(IPluginContext pluginCtx);

    public boolean isWriterSupportMultiTableInReader(IPluginContext pluginCtx);

    /**
     * dataX配置文件列表
     *
     * @return
     */
    public List<String> getDataxCfgFileNames(IPluginContext pluginCtx);

    /**
     * 表映射
     *
     * @return key: fromTabName
     */
    public Map<String, TableAlias> getTabAlias();

    /**
     * 表别名
     */
    public class TableAlias {
        private String from;
        private String to;

        public TableAlias() {
        }

        public TableAlias(String from) {
            this.from = from;
            this.to = from;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        @Override
        public String toString() {
            return "TableMap{" +
                    "from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    '}';
        }
    }

    /**
     * 标示DataXWriter会自己创建IDataxProcessor.TableMap实例，使用这个标示必须满足isSupportMultiTable为false，具体例子可以看DataXMongodbWriter
     */
    public interface INullTableMapCreator {
    }

    /**
     * 类似MySQL(A库)导入MySQL(B库) A库中的一张a表可能对应的B库的表为aa表名称会不一致，
     */
    public class TableMap extends TableAlias {

        private List<ISelectedTab.ColMeta> sourceCols;

        public List<ISelectedTab.ColMeta> getSourceCols() {
            return sourceCols;
        }

        public void setSourceCols(List<ISelectedTab.ColMeta> sourceCols) {
            this.sourceCols = sourceCols;
        }

    }

    public class TabCols {
        private final List<String> cols;

        public static TabCols create(TableMap tm) {
            return new TabCols(tm.getSourceCols().stream().map((c) -> c.getName()).collect(Collectors.toList()));
        }

        private TabCols(List<String> cols) {
            this.cols = cols;
        }

        public String getColsQuotes() {
            return getColumnWithLink("\"`", "`\"");
        }

        public String getCols() {
            return getColumnWithLink("`", "`");
        }

        private String getColumnWithLink(String left, String right) {
            return this.cols.stream().map(r -> (left + r + right)).collect(Collectors.joining(","));
        }
    }
}
