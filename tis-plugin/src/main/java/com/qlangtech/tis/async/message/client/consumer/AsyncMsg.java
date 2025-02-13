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
package com.qlangtech.tis.async.message.client.consumer;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

/**
 * 异步Notify消息
 *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2020/04/13
 */
public interface AsyncMsg<SOURCE> extends Serializable {

    /**
     * 关注的表,原始表表名
     *
     * @return
     */
    Set<String> getFocusTabs();

    /**
     * Topic
     *
     * @return
     */
    String getTopic();

    /**
     * Tag
     *
     * @return
     */
    String getTag();

    /**
     * 消息内容
     *
     * @return
     */
    SOURCE getSource() throws IOException;

    /**
     * MsgID
     *
     * @return
     */
    String getMsgID();

    /**
     * Key
     *
     * @return
     */
    // String getKey();

//    /**
//     * 重试次数
//     *
//     * @return
//     */
//    int getReconsumeTimes();

//    /**
//     * 开始投递的时间
//     *
//     * @return
//     */
//    long getStartDeliverTime();

//    /**
//     * 最初的MessageID。在消息重试时msgID会变
//     *
//     * @return
//     */
//    String getOriginMsgID();
}
