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

package com.qlangtech.tis.compiler.java;

import javax.tools.JavaFileObject;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-10-21 09:42
 **/
public class SourceGetterStrategy {

    public final boolean getResource;

    public final String childSourceDir;

    public final String sourceCodeExtendsion;

    public SourceGetterStrategy(boolean getResource, String childSourceDir, String sourceCodeExtendsion) {
        this.getResource = getResource;
        this.childSourceDir = childSourceDir;
        this.sourceCodeExtendsion = sourceCodeExtendsion;
    }

    public MyJavaFileObject processMyJavaFileObject(MyJavaFileObject fileObj) {
        return fileObj;
    }

    public JavaFileObject.Kind getSourceKind() {
        return JavaFileObject.Kind.SOURCE;
    }
}
