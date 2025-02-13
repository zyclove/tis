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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @date 2019年10月10日
 */
public interface IOutputEntry {

    String KEY_COMPILE_FAILD_FILE = "compile_faild";

    public ByteArrayOutputStream getOutputStream() throws IOException;

    public ZipPath getZipPath();

    public JavaFileObject getFileObject();

    public void processSource(JarOutputStream jaroutput) throws Exception;

    public boolean containCompiledClass();
}
