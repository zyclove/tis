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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.qlangtech.tis.extension.Describable;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.IPropertyType;
import com.qlangtech.tis.extension.PluginFormProperties;
import com.qlangtech.tis.extension.impl.IOUtils;
import com.qlangtech.tis.extension.impl.PropertyType;
import com.qlangtech.tis.extension.impl.SuFormProperties;
import com.qlangtech.tis.plugin.IdentityName;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import org.apache.commons.lang.ClassUtils;

import java.util.*;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @date 2020/04/13
 */
public class DescriptorsJSON<T extends Describable<T>> {

    public static final String KEY_DISPLAY_NAME = "displayName";
    public static final String KEY_EXTEND_POINT = "extendPoint";
    public static final String KEY_IMPL = "impl";

    private final List<Descriptor<T>> descriptors;

    public DescriptorsJSON(List<Descriptor<T>> descriptors) {
        this.descriptors = descriptors;
        // descriptors.stream().findFirst();
    }

    public DescriptorsJSON(Descriptor<T> descriptor) {
        //  this.descriptors = Collections.singletonList(descriptor);
        this(Collections.singletonList(descriptor));
    }

    public JSONObject getDescriptorsJSON() {
        return getDescriptorsJSON(Optional.empty());
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public static abstract class SubFormFieldVisitor implements PluginFormProperties.IVisitor {
        @Override
        public final Void visit(SuFormProperties props) {
            JSONObject behaviorMeta = null;
            List allSuperclasses = Lists.newArrayList(props.parentClazz);
            allSuperclasses.addAll(ClassUtils.getAllSuperclasses(props.parentClazz));

            Class superClass = null;
            for (Object clazz : allSuperclasses) {
                superClass = (Class) clazz;
                String jsonMeta = IOUtils.loadResourceFromClasspath(superClass
                        , superClass.getSimpleName() + "." + props.getSubFormFieldName() + ".json", false);
                if (jsonMeta != null) {
                    behaviorMeta = JSON.parseObject(jsonMeta);
                    break;
                }
            }


            visitSubForm(behaviorMeta, props);
            return null;
        }

        /**
         * @param behaviorMeta 可能为空 结构可以查阅：com/qlangtech/tis/plugin/datax/DataxMySQLReader.selectedTabs.json
         * @param props
         */
        protected abstract void visitSubForm(JSONObject behaviorMeta, SuFormProperties props);
    }

    public JSONObject getDescriptorsJSON(Optional<IPropertyType.SubFormFilter> subFormFilter) {

        JSONArray attrs;
        String key;
        PropertyType val;
        JSONObject extraProps = null;
        // FormField fieldAnnot;
        JSONObject attrVal;
        JSONObject descriptors = new JSONObject();
        Map<String, Object> extractProps;
        // IPropertyType.SubFormFilter subFilter = null;
        PluginFormProperties pluginFormPropertyTypes;
        for (Descriptor<T> d : this.descriptors) {
            pluginFormPropertyTypes = d.getPluginFormPropertyTypes(subFormFilter);

            JSONObject des = new JSONObject();
            pluginFormPropertyTypes.accept(new SubFormFieldVisitor() {
                @Override
                public void visitSubForm(JSONObject behaviorMeta, SuFormProperties props) {
                    JSONObject subForm = new JSONObject();
                    if (behaviorMeta != null) {
                        subForm.put("behaviorMeta", behaviorMeta);
                    }
                    subForm.put("fieldName", props.getSubFormFieldName());
                    if (subFormFilter.isPresent()) {
                        subForm.put("idList", props.getSubFormIdListGetter().build(subFormFilter.get()));
                    }
                    des.put("subFormMeta", subForm);
                    des.put("subForm", true);
                }
            });
            des.put(KEY_DISPLAY_NAME, d.getDisplayName());
            des.put(KEY_EXTEND_POINT, d.getT().getName());
            des.put(KEY_IMPL, d.getId());
            des.put("veriflable", d.overWriteValidateMethod);
            if (IdentityName.class.isAssignableFrom(d.clazz)) {
                des.put("pkField", d.getIdentityField().displayName);
            }
            extractProps = d.getExtractProps();
            if (!extractProps.isEmpty()) {
                des.put("extractProps", extractProps);
            }

            attrs = new JSONArray();
            ArrayList<Map.Entry<String, PropertyType>> entries
                    = Lists.newArrayList(pluginFormPropertyTypes.getKVTuples());

            entries.sort(((o1, o2) -> o1.getValue().ordinal() - o2.getValue().ordinal()));
            for (Map.Entry<String, PropertyType> pp : entries) {
                key = pp.getKey();
                val = pp.getValue();
                // fieldAnnot = val.getFormField();
                attrVal = new JSONObject();
                attrVal.put("key", key);
                // 是否是主键
                attrVal.put("pk", val.isIdentity());
                attrVal.put("describable", val.isDescribable());
                attrVal.put("type", val.typeIdentity());
                attrVal.put("required", val.isInputRequired());
                attrVal.put("ord", val.ordinal());

                extraProps = val.getExtraProps();
                if (extraProps != null) {
                    // 额外属性
                    attrVal.put("eprops", extraProps);
                }

                ISelectOptionsGetter optionsCreator = null;
                if (val.typeIdentity() == FormFieldType.SELECTABLE.getIdentity()) {
                    if (!(d instanceof ISelectOptionsGetter)) {
                        throw new IllegalStateException("descriptor:" + d.getClass()
                                + " has a selectable field:" + key + " descriptor must be an instance of 'ISelectOptionsGetter'");
                    }
                    optionsCreator = d;
                    List<Descriptor.SelectOption> selectOptions = optionsCreator.getSelectOptions(key);
                    attrVal.put("options", selectOptions);
                }
                if (val.isDescribable()) {
                    DescriptorsJSON des2Json = new DescriptorsJSON(val.getApplicableDescriptors());
                    attrVal.put("descriptors", des2Json.getDescriptorsJSON());
                }
                // attrs.put(attrVal);
                attrs.add(attrVal);
            }
            // 对象拥有的属性
            des.put("attrs", attrs);
            // processor.process(attrs.keySet(), d);
            descriptors.put(d.getId(), des);
        }
        return descriptors;
    }

    public interface IPropGetter {
        public Object build(IPropertyType.SubFormFilter filter);
    }
}
