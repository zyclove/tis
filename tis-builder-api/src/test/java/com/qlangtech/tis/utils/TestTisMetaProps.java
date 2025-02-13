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

package com.qlangtech.tis.utils;

import junit.framework.TestCase;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-11-25 20:38
 **/
public class TestTisMetaProps extends TestCase {

    public void testGetInstance() {
        TisMetaProps i = TisMetaProps.getInstance();
        //  i.getVersion();
        assertNotNull(i.getVersion());

        Pattern versionPattern = Pattern.compile("\\d+\\.\\d+\\.\\d+");
        Matcher matcher = versionPattern.matcher(i.getVersion());
        assertTrue(i.getVersion(), matcher.matches());
    }
}
