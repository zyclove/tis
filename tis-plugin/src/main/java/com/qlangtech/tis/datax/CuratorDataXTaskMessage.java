/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 *   This program is free software: you can use, redistribute, and/or modify
 *   it under the terms of the GNU Affero General Public License, version 3
 *   or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *   FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.qlangtech.tis.datax;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-05-06 15:01
 **/
public class CuratorDataXTaskMessage {
    public static final String SYSTEM_KEY_LOGBACK_PATH_KEY = "logback.configurationFile";
    public static final String SYSTEM_KEY_LOGBACK_PATH_VALUE = "logback-datax.xml";
    private String dataXName;

    private Integer jobId;

    private String jobName;
    // 估计总记录数目
    private Integer allRowsApproximately;

    public Integer getAllRowsApproximately() {
        return allRowsApproximately;
    }

    public void setAllRowsApproximately(Integer allRowsApproximately) {
        this.allRowsApproximately = allRowsApproximately;
    }

    public String getDataXName() {
        return dataXName;
    }

    public Integer getJobId() {
        return jobId;
    }


    public String getJobName() {
        return jobName;
    }

    public void setDataXName(String dataXName) {
        this.dataXName = dataXName;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

//    public void setJobPath(String jobPath) {
//        this.jobPath = jobPath;
//    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
}
