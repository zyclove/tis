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
package com.qlangtech.tis.sql.parser.tuple.creator;

import com.qlangtech.tis.sql.parser.tuple.creator.impl.PropGetter;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @date 2021-04-04 17:35
 */
public interface IValChain {
    public Stream<PropGetter> chainStream();

    public PropGetter first();

    public PropGetter last();

    public <R> Stream<R> mapChainValve(Function<PropGetter, ? extends R> mapper);

    boolean useAliasOutputName();

    public boolean hasFuncTuple();
}
