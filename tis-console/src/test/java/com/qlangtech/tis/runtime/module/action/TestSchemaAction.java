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
package com.qlangtech.tis.runtime.module.action;

import com.alibaba.fastjson.JSONObject;
import com.opensymphony.xwork2.ActionProxy;
import com.qlangtech.tis.BasicActionTestCase;
import com.qlangtech.tis.manage.biz.dal.pojo.UploadResource;
import com.qlangtech.tis.manage.common.Config;
import com.qlangtech.tis.manage.common.HttpUtils;
import com.qlangtech.tis.manage.common.SnapshotDomain;
import com.qlangtech.tis.manage.common.TisUTF8;
import com.qlangtech.tis.manage.common.valve.AjaxValve;
import com.qlangtech.tis.trigger.util.JsonUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @date 2021-01-29 17:55
 */
public class TestSchemaAction extends BasicActionTestCase {

  public static final String collection = "search4totalpay";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Config.setConfig(null);
    HttpUtils.addMockApply(-1, "solrschema.dtd", new HttpUtils.IClasspathRes() {
      @Override
      public InputStream getResourceAsStream(URL url) {
        try {
          return FileUtils.openInputStream(new File("./webapp/dtd/solrschema.dtd"));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public void testGetFieldsBySnapshotId() throws Exception {
    request.setParameter("emethod", "getFieldsBySnapshotId");
    request.setParameter("action", "schema_action");
    request.setParameter("snapshot", "21123");
    request.setParameter("editable", "false");
    request.setParameter("restype", "schema.xml");
    setCollection(collection);

    ActionProxy proxy = getActionProxy();

    String result = proxy.execute();
    assertEquals("SchemaAction_ajax", result);
    AjaxValve.ActionExecResult aResult = showBizResult();
    assertNotNull(aResult);
    assertTrue(aResult.isSuccess());

    JSONObject biz = (JSONObject) aResult.getBizResult();
    assertNotNull(biz);
    String assertFileName = "assert-testGetFieldsBySnapshotId.json";
    try (InputStream assertInput = this.getClass().getResourceAsStream(assertFileName)) {
      assertNotNull(assertInput);
      //  FileUtils.write(new File("testGetFieldsBySnapshotId.json"), JsonUtil.toString(biz), TisUTF8.get());
      assertEquals(IOUtils.toString(assertInput, TisUTF8.get()), JsonUtil.toString(biz));
    }
  }


  /**
   * 测试专家模式Schema保存, 将mall_id类型由string改成test类型,并且去掉了一个动态字段pt_*
   *
   * @throws Exception
   */
  public void testDoSaveByExpertModel() throws Exception {
    // event_submit_do_save_by_expert_model: y
    request.setParameter("emethod", "saveByExpertModel");
    request.setParameter("action", "schema_action");
    //request.addHeader("appname", collection);
    setCollection(collection);

    try (InputStream post = this.getClass().getResourceAsStream("schema-action-do-save-by-expert-model.json")) {
      request.setContent(IOUtils.toByteArray(post));
    }

    ActionProxy proxy = getActionProxy();

    String result = proxy.execute();
    assertEquals("SchemaAction_ajax", result);
    AjaxValve.ActionExecResult aResult = showBizResult();
    assertNotNull(aResult);
    assertTrue(aResult.isSuccess());
    // this.verifyAll();
    SchemaAction.CreateSnapshotResult bizResult = (SchemaAction.CreateSnapshotResult) aResult.getBizResult();
    assertNotNull(bizResult);
    Integer newSnapshotId = bizResult.getNewId();
    assertTrue("newSnapshotId can not be null", newSnapshotId > 0);
    SnapshotDomain snapshotView = runContext.getSnapshotViewDAO().getView(newSnapshotId);
    assertNotNull("snapshotView can not be null", snapshotView);

    UploadResource solrSchema = snapshotView.getSolrSchema();
    String mallIdTypeModifiedXml = StringUtils.remove(new String(solrSchema.getContent(), TisUTF8.get()), '\r');

    try (InputStream input = this.getClass().getResourceAsStream("schema-action-do-save-by-expert-model-modify-mallid-type-assert.xml")) {
      assertNotNull(input);
      String mallIdTypeModifiedXmlExpect = StringUtils.trimToEmpty(IOUtils.toString(input, TisUTF8.get()));

      assertEquals(mallIdTypeModifiedXmlExpect, StringUtils.trimToEmpty(mallIdTypeModifiedXml));
    }

//    JsonUtil.assertJSONEqual(TestSchemaAction.class, "schema-action-do-save-by-expert-model-modify-mallid-type-assert.xml"
//      , mallIdTypeModifiedXml, (msg, e, a) -> {
//        assertEquals(msg, e, a);
//      });

  }


  private ActionProxy getActionProxy() {
    ActionProxy proxy = getActionProxy("/runtime/jarcontent/schema.ajax");
    assertNotNull(proxy);
    SchemaAction schemaAction = (SchemaAction) proxy.getAction();
    assertNotNull(schemaAction);
    return proxy;
  }
}
