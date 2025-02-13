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
package com.qlangtech.tis.coredefine.module.action;

import com.alibaba.citrus.turbine.Context;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.assemble.ExecResult;
import com.qlangtech.tis.assemble.FullbuildPhase;
import com.qlangtech.tis.datax.DataXJobSubmit;
import com.qlangtech.tis.datax.IDataxProcessor;
import com.qlangtech.tis.datax.ISearchEngineTypeTransfer;
import com.qlangtech.tis.datax.impl.*;
import com.qlangtech.tis.datax.job.DataXJobWorker;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.DescriptorExtensionList;
import com.qlangtech.tis.extension.IPropertyType;
import com.qlangtech.tis.manage.IAppSource;
import com.qlangtech.tis.manage.PermissionConstant;
import com.qlangtech.tis.manage.biz.dal.pojo.Application;
import com.qlangtech.tis.manage.biz.dal.pojo.ApplicationCriteria;
import com.qlangtech.tis.manage.common.*;
import com.qlangtech.tis.manage.common.apps.IDepartmentGetter;
import com.qlangtech.tis.manage.common.valve.AjaxValve;
import com.qlangtech.tis.manage.servlet.BasicServlet;
import com.qlangtech.tis.manage.spring.aop.Func;
import com.qlangtech.tis.offline.DataxUtils;
import com.qlangtech.tis.order.center.IParamContext;
import com.qlangtech.tis.plugin.KeyedPluginStore;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.plugin.ds.ColumnMetaData;
import com.qlangtech.tis.plugin.ds.ISelectedTab;
import com.qlangtech.tis.runtime.module.action.BasicModule;
import com.qlangtech.tis.runtime.module.action.CreateIndexConfirmModel;
import com.qlangtech.tis.runtime.module.action.SchemaAction;
import com.qlangtech.tis.runtime.module.misc.IControlMsgHandler;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import com.qlangtech.tis.runtime.module.misc.impl.DelegateControl4JsonPostMsgHandler;
import com.qlangtech.tis.util.*;
import com.qlangtech.tis.workflow.pojo.WorkFlowBuildHistory;
import com.qlangtech.tis.workflow.pojo.WorkFlowBuildHistoryCriteria;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.convention.annotation.InterceptorRef;
import org.apache.struts2.convention.annotation.InterceptorRefs;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * manage DataX pipe process logic
 *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2021-04-08 15:04
 */
@InterceptorRefs({@InterceptorRef("tisStack")})
public class DataxAction extends BasicModule {

  private static final String PARAM_KEY_DATAX_NAME = DataxUtils.DATAX_NAME;

  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doTriggerFullbuildTask(Context context) throws Exception {

    DataXJobSubmit.InstanceType triggerType = DataXJobSubmit.getDataXTriggerType();
    Optional<DataXJobSubmit> dataXJobSubmit = DataXJobSubmit.getDataXJobSubmit(triggerType);
    if (!dataXJobSubmit.isPresent()) {
      this.setBizResult(context, Collections.singletonMap("installLocal", true));
      this.addErrorMessage(context, "还没有安装本地触发类型的执行器:" + triggerType + ",请先安装");
      return;
    }

    List<HttpUtils.PostParam> params = Lists.newArrayList();
    params.add(new HttpUtils.PostParam(CoreAction.KEY_APPNAME, this.getCollectionName()));
    params.add(new HttpUtils.PostParam(IParamContext.COMPONENT_START, FullbuildPhase.FullDump.getName()));
    params.add(new HttpUtils.PostParam(IParamContext.COMPONENT_END, FullbuildPhase.FullDump.getName()));

    this.setBizResult(context, CoreAction.triggerBuild(this, context, params));
  }


  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doSaveTableCreateDdl(Context context) throws Exception {
    JSONObject post = this.parseJsonPost();
    String dataXName = post.getString(DataxUtils.DATAX_NAME);
    String createTableDDL = post.getString("content");
    if (StringUtils.isEmpty(createTableDDL)) {
      throw new IllegalArgumentException("create table ddl can not be null");
    }
    if (StringUtils.isEmpty(dataXName)) {
      throw new IllegalArgumentException("param dataXName can not be null");
    }

    DataxProcessor dataxProcessor = IAppSource.load(this, dataXName);
    String createFileName = post.getString("fileName");
    dataxProcessor.saveCreateTableDDL(this, new StringBuffer(createTableDDL), createFileName, true);
    this.addActionMessage(context, "已经成功更新建表DDL脚本 " + createFileName);
  }


  /**
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doDataxProcessorDesc(Context context) throws Exception {

    UploadPluginMeta pluginMeta = UploadPluginMeta.parse(HeteroEnum.APP_SOURCE.identity);
    HeteroList<IAppSource> hlist = new HeteroList<>(pluginMeta);
    hlist.setDescriptors(Collections.singletonList(DataxProcessor.getPluginDescMeta()));
    hlist.setExtensionPoint(IAppSource.class);
    hlist.setSelectable(Selectable.Single);
    hlist.setCaption(StringUtils.EMPTY);

    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    if (StringUtils.isNotEmpty(dataxName)) {
      hlist.setItems(Collections.singletonList(DataxProcessor.load(this, dataxName)));
    }

    this.setBizResult(context, hlist.toJSON());
  }

  /**
   * 取得写插件配置
   *
   * @param context
   * @throws Exception
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetWriterPluginInfo(Context context) throws Exception {
    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    JSONObject writerDesc = this.parseJsonPost();
    if (StringUtils.isEmpty(dataxName)) {
      throw new IllegalStateException("param " + PARAM_KEY_DATAX_NAME + " can not be null");
    }
    DataxReader.load(this, dataxName);
    KeyedPluginStore<DataxWriter> writerStore = DataxWriter.getPluginStore(this, dataxName);
    DataxWriter writer = writerStore.getPlugin();
    Map<String, Object> pluginInfo = Maps.newHashMap();
    final String requestDescId = writerDesc.getString("impl");
    if (writer != null && StringUtils.equals(writer.getDescriptor().getId(), requestDescId)) {
      DataxReader readerPlugin = DataxReader.load(this, dataxName);
      DataxWriter.BaseDataxWriterDescriptor writerDescriptor = (DataxWriter.BaseDataxWriterDescriptor) writer.getDescriptor();
      if (!writerDescriptor.isSupportMultiTable() && readerPlugin.getSelectedTabs().size() > 1) {
        // 这种情况是不允许的，例如：elastic这样的writer中对于column的设置比较复杂，需要在writer plugin页面中完成，所以就不能支持在reader中选择多个表了
        throw new IllegalStateException("status is not allowed:!writerDescriptor.isSupportMultiTable() && readerPlugin.hasMulitTable()");
      }
      pluginInfo.put("item", (new DescribableJSON(writer)).getItemJson());
    }
    pluginInfo.put("desc", new DescriptorsJSON(TIS.get().getDescriptor(requestDescId)).getDescriptorsJSON());
    this.setBizResult(context, pluginInfo);
  }

  /**
   * 取得读插件配置
   *
   * @param context
   * @throws Exception
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetReaderPluginInfo(Context context) throws Exception {
    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    JSONObject readerDesc = this.parseJsonPost();
    if (StringUtils.isEmpty(dataxName)) {
      throw new IllegalStateException("param " + PARAM_KEY_DATAX_NAME + " can not be null");
    }
    final String requestDescId = readerDesc.getString("impl");
    KeyedPluginStore<DataxReader> readerStore = DataxReader.getPluginStore(this, dataxName);
    DataxReader reader = readerStore.getPlugin();
    Map<String, Object> pluginInfo = Maps.newHashMap();
    if (reader != null && StringUtils.equals(reader.getDescriptor().getId(), requestDescId)) {
      pluginInfo.put("item", (new DescribableJSON(reader)).getItemJson());
    }
    pluginInfo.put("desc", new DescriptorsJSON(TIS.get().getDescriptor(requestDescId)).getDescriptorsJSON());
    this.setBizResult(context, pluginInfo);
  }

  /**
   * 启动DataX执行器
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doLaunchDataxWorker(Context context) {

    DataXJobWorker dataxJobWorker = DataXJobWorker.getJobWorker(this.getK8SJobWorkerTargetName());
    if (dataxJobWorker == null) {
      throw new IllegalStateException("dataxJobWorker can not be null,relevant target type:" + this.getK8SJobWorkerTargetName());
    }

    if (dataxJobWorker.inService()) {
      throw new IllegalStateException("dataxJobWorker is in serivce ,can not launch repeat");
    }

    dataxJobWorker.launchService();
    try {
      Thread.sleep(4000l);
    } catch (InterruptedException e) {

    }
    this.doGetJobWorkerMeta(context);
    AjaxValve.ActionExecResult actionExecResult = MockContext.getActionExecResult();
    DataXJobWorkerStatus jobWorkerStatus = (DataXJobWorkerStatus) actionExecResult.getBizResult();
    if (jobWorkerStatus == null || !jobWorkerStatus.isK8sReplicationControllerCreated()) {
      throw new IllegalStateException("Job Controller launch faild please contract administer");
    }
    this.addActionMessage(context, "已经成功启动DataX执行器");
  }

  /**
   * 删除dataX实例
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doRemoveDataxWorker(Context context) {

    DataXJobWorker jobWorker = DataXJobWorker.getJobWorker(this.getK8SJobWorkerTargetName());

//    PluginStore<DataXJobWorker> dataxJobWorkerStore = TIS.getPluginStore(DataXJobWorker.class);
//    DataXJobWorker dataxJobWorker = dataxJobWorkerStore.getPlugin();
    if (!jobWorker.inService()) {
      throw new IllegalStateException("dataxJobWorker is not in serivce ,can not remove");
    }
    jobWorker.remove();
    this.addActionMessage(context, "DataX Worker 已经被删除");
  }

  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doWorkerDesc(Context context) {
    final TargetResName targetName = getK8SJobWorkerTargetName();

    DataXJobWorker jobWorker = DataXJobWorker.getJobWorker(targetName);
    if (jobWorker != null && jobWorker.inService()) {
      throw new IllegalStateException("dataX worker is on duty");
    }

    this.setBizResult(context, new PluginDescMeta(DataXJobWorker.getDesc(targetName)));
  }


  /**
   * 取得K8S dataX worker
   *
   * @param context
   */

  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetDataxWorkerMeta(Context context) {
    getJobWoker(context, DataXJobWorker.K8S_DATAX_INSTANCE_NAME);
  }

  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetJobWorkerMeta(Context context) {
    // PluginStore<DataXJobWorker> dataxJobWorkerStore = TIS.getPluginStore(DataXJobWorker.class);
    final TargetResName targetName = getK8SJobWorkerTargetName();
    getJobWoker(context, targetName);
  }

  private void getJobWoker(Context context, TargetResName targetName) {
    Optional<DataXJobWorker> firstWorker
      = Optional.ofNullable(DataXJobWorker.getJobWorker((targetName))); //dataxJobWorkerStore.getPlugins().stream().filter((p) -> isJobWorkerMatch(targetName, p.getDescriptor())).findFirst();

    DataXJobWorkerStatus jobWorkerStatus = new DataXJobWorkerStatus();
    if (!firstWorker.isPresent()) {
      jobWorkerStatus.setK8sReplicationControllerCreated(false);
      this.setBizResult(context, jobWorkerStatus);
      return;
    }
    DataXJobWorker jobWorker = firstWorker.get();
    boolean disableRcdeployment = this.getBoolean("disableRcdeployment");
    jobWorkerStatus.setK8sReplicationControllerCreated(jobWorker.inService());
    if (jobWorkerStatus.isK8sReplicationControllerCreated() && !disableRcdeployment) {
      jobWorkerStatus.setRcDeployment(jobWorker.getRCDeployment());
    }
    this.setBizResult(context, jobWorkerStatus);
  }

  private TargetResName getK8SJobWorkerTargetName() {
    final String targetName = this.getString("targetName");
    DataXJobWorker.validateTargetName(targetName);
    return new TargetResName(targetName);
  }

  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetDataxWorkerHpa(Context context) {
    DataXJobWorker jobWorker = DataXJobWorker.getJobWorker(this.getK8SJobWorkerTargetName());
    if (jobWorker.getHpa() != null) {
      RcHpaStatus hpaStatus = jobWorker.getHpaStatus();
      this.setBizResult(context, hpaStatus);
    }
  }

  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doRelaunchPodProcess(Context context) throws Exception {
    DataXJobWorker jobWorker = DataXJobWorker.getJobWorker(this.getK8SJobWorkerTargetName());
    String podName = this.getString("podName");
    jobWorker.relaunch(podName);
//    PluginStore<IncrStreamFactory> incrStreamStore = getIncrStreamFactoryStore(this, true);
//    IncrStreamFactory incrStream = incrStreamStore.getPlugin();
//    IRCController incrSync = incrStream.getIncrSync();
//    incrSync.relaunch(this.getCollectionName());
  }

  /**
   * 保存K8S dataX worker
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doSaveDataxWorker(Context context) {
    JSONObject postContent = this.parseJsonPost();
    JSONObject k8sSpec = postContent.getJSONObject("k8sSpec");
    //JSONObject dataxWorker = postContent.getJSONObject("dataxWorker");

    // UploadPluginMeta pluginMeta = UploadPluginMeta.parse(HeteroEnum.DATAX_WORKER.identity);

//    JSONArray itemsArray = new JSONArray();
//    itemsArray.add(dataxWorker);
//    PluginAction.PluginItemsParser pluginItemsParser
//      = PluginAction.parsePluginItems(this, pluginMeta
//      , context, 0, dataxWorker.getJSONArray("items"), false);

//    if (pluginItemsParser.faild) {
//
//      return;
//    }

    IncrUtils.IncrSpecResult incrSpecResult = IncrUtils.parseIncrSpec(context, k8sSpec, this);
    if (!incrSpecResult.isSuccess()) {
      return;
    }

    TargetResName resName = this.getK8SJobWorkerTargetName();
    DataXJobWorker worker = DataXJobWorker.getJobWorker(resName);
    // PluginStore<DataXJobWorker> jobWorkerStore = TIS.getPluginStore(DataXJobWorker.class);
    // List<DataXJobWorker> jobWorkers = jobWorkerStore.getPlugins();
    // List<Descriptor.ParseDescribable<DataXJobWorker>> dlist = Lists.newArrayList();
    // boolean setted = false;
    // for (DataXJobWorker worker : jobWorkers) {
    //if (StringUtils.equals(resName.getName(), worker.identityValue())) {
    worker.setReplicasSpec(incrSpecResult.getSpec());
    if (incrSpecResult.hpa != null) {
      worker.setHpa(incrSpecResult.hpa);
    }
    DataXJobWorker.setJobWorker(resName, worker);
    //   setted = true;
    //}
    // dlist.add(PluginStore.getDescribablesWithMeta(jobWorkerStore, worker));
    // }
//    if (!setted) {
//      throw new IllegalStateException("has not setted");
//    }

    // jobWorkerStore.setPlugins(this, Optional.empty(), dlist, true);


//    Descriptor.ParseDescribable<DataXJobWorker> describablesWithMeta
//      = PluginStore.getDescribablesWithMeta(jobWorkerStore, DataXJobWorker.getJobWorker());
//    DataXJobWorker dataxJobWorker = describablesWithMeta.instance;
//    Objects.requireNonNull(dataxJobWorker, "dataxJobWorker can not be null");
//
//    //IPluginContext pluginContext, Optional<Context> context, List<Descriptor.ParseDescribable<T>> dlist, boolean update
//    List<Descriptor.ParseDescribable<DataXJobWorker>> dlist = Collections.singletonList(describablesWithMeta);
//    jobWorkerStore.setPlugins(this, Optional.empty(), dlist, true);
  }


  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetSupportedReaderWriterTypes(Context context) {

    DescriptorExtensionList<DataxReader, Descriptor<DataxReader>> readerTypes = TIS.get().getDescriptorList(DataxReader.class);
    DescriptorExtensionList<DataxWriter, Descriptor<DataxWriter>> writerTypes = TIS.get().getDescriptorList(DataxWriter.class);

    this.setBizResult(context, new DataxPluginDescMeta(readerTypes, writerTypes));
  }

  enum GenCfgFileType {
    DATAX_CFG("datax"), CREATE_TABLE_DDL("createTableDDL");
    private final String val;

    static GenCfgFileType parse(String val) {
      for (GenCfgFileType t : GenCfgFileType.values()) {
        if (t.val.equalsIgnoreCase(val)) {
          return t;
        }
      }
      throw new IllegalStateException("illegal val:" + val);
    }

    private GenCfgFileType(String val) {
      this.val = val;
    }
  }


  /**
   * 取得生成的配置文件的内容
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetGenCfgFile(Context context) throws Exception {
    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    String fileName = this.getString("fileName");
    GenCfgFileType fileType = GenCfgFileType.parse(this.getString("fileType"));
    DataxProcessor dataxProcessor = IAppSource.load(this, dataxName);
    Map<String, Object> fileMeta = Maps.newHashMap();
    switch (fileType) {
      case DATAX_CFG:
        File dataxCfgDir = dataxProcessor.getDataxCfgDir(this);
        File cfgFile = new File(dataxCfgDir, fileName);
        if (!cfgFile.exists()) {
          throw new IllegalStateException("target file:" + cfgFile.getAbsolutePath());
        }
        fileMeta.put("content", FileUtils.readFileToString(cfgFile, TisUTF8.get()));
        break;
      case CREATE_TABLE_DDL:
        File ddlDir = dataxProcessor.getDataxCreateDDLDir(this);
        File sqlScript = new File(ddlDir, fileName);
        if (!sqlScript.exists()) {
          throw new IllegalStateException("target file:" + sqlScript.getAbsolutePath());
        }
        fileMeta.put("content", FileUtils.readFileToString(sqlScript, TisUTF8.get()));
        break;
      default:
        throw new IllegalStateException("illegal fileType:" + fileType);
    }


    this.setBizResult(context, fileMeta);
  }

  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetExecStatistics(Context context) throws Exception {
    WorkFlowBuildHistoryCriteria historyCriteria = new WorkFlowBuildHistoryCriteria();
    Date from = ManageUtils.getOffsetDate(-7);
    SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd");
    Map<String, DataXExecStatus> execStatis = Maps.newTreeMap();
    ExecResult execResult = null;
    DataXExecStatus execStatus = null;
    String timeLab = null;
    for (int i = 0; i < 8; i++) {
      timeLab = dateFormat.format(ManageUtils.getOffsetDate(-i));
      execStatis.put(timeLab, new DataXExecStatus(timeLab));
    }
    int successCount = 0;
    int errCount = 0;
    historyCriteria.createCriteria().andAppIdEqualTo(this.getAppDomain().getAppid()).andCreateTimeGreaterThan(from);
    for (WorkFlowBuildHistory h : this.wfDAOFacade.getWorkFlowBuildHistoryDAO().selectByExample(historyCriteria)) {
      execResult = ExecResult.parse(h.getState());
      execStatus = execStatis.get(dateFormat.format(h.getCreateTime()));
      if (execStatus == null) {
        continue;
      }
      if (execResult == ExecResult.SUCCESS) {
        execStatus.successCount++;
        successCount++;
      } else if (execResult == ExecResult.FAILD) {
        execStatus.errCount++;
        errCount++;
      }
    }
    Map<String, Object> bizResult = Maps.newHashMap();
    bizResult.put("data", execStatis.values());
    Map<String, Integer> allStatis = Maps.newHashMap();
    allStatis.put("errCount", errCount);
    allStatis.put("successCount", successCount);
    bizResult.put("statis", allStatis);
    this.setBizResult(context, bizResult);
  }

  private static class DataXExecStatus {

    private final String timeLab;

    public DataXExecStatus(String timeLab) {
      this.timeLab = timeLab;
    }

    public String getTimeLab() {
      return timeLab;
    }

    int errCount;
    int successCount;

    public int getErrCount() {
      return errCount;
    }

    public int getSuccessCount() {
      return successCount;
    }
  }

  /**
   * @param context
   * @throws Exception
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doValidateDataxProfile(Context context) throws Exception {
    Application app = this.parseJsonPost(Application.class);
    SchemaAction.CreateAppResult validateResult = this.createNewApp(context, app
      , true, (newAppId) -> {
        throw new UnsupportedOperationException();
      });
  }

  public static List<Option> deps;

  /**
   * DataX中显示已有部门
   *
   * @param
   * @return
   */
  public static List<Option> getDepartments() {
    if (deps != null) {
      return deps;
    }
    RunContext runContext = BasicServlet.getBeanByType(ServletActionContext.getServletContext(), RunContext.class);
    Objects.requireNonNull(runContext, "runContext can not be null");
    return BasicModule.getDptList(runContext, new IDepartmentGetter() {
    });
  }

  /**
   * 重新生成datax配置文件
   *
   * @param context
   * @throws Exception
   */
  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doGenerateDataxCfgs(Context context) throws Exception {
    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    boolean getExist = this.getBoolean("getExist");
    DataxProcessor dataxProcessor = IAppSource.load(this, dataxName);

    DataXCfgGenerator cfgGenerator = new DataXCfgGenerator(this, dataxName, dataxProcessor);
    File dataxCfgDir = dataxProcessor.getDataxCfgDir(this);

    if (!getExist) {
      FileUtils.forceMkdir(dataxCfgDir);
      // 先清空文件
      FileUtils.cleanDirectory(dataxCfgDir);
    }
    this.setBizResult(context, getExist ? cfgGenerator.getExistCfg(dataxCfgDir) : cfgGenerator.startGenerateCfg(dataxCfgDir));
  }


  /**
   * 创建DataX实例
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doCreateDatax(Context context) throws Exception {
    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    DataxProcessor dataxProcessor = IAppSource.load(null, dataxName);
    Application app = dataxProcessor.buildApp();

    SchemaAction.CreateAppResult createAppResult = this.createNewApp(context, app
      , false, (newAppId) -> {
        SchemaAction.CreateAppResult appResult = new SchemaAction.CreateAppResult();
        appResult.setSuccess(true);
        appResult.setNewAppId(newAppId);
        return appResult;
      });
  }

  private static final Pattern PatternEdittingDirSuffix = Pattern.compile("\\-[\\da-z]{8}\\-[\\da-z]{4}\\-[\\da-z]{4}\\-[\\da-z]{4}\\-[\\da-z]{12}");

  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doUpdateDatax(Context context) throws Exception {
    String dataxName = this.getCollectionName();
    DataxProcessor old = IAppSource.load(null, dataxName);
    DataxProcessor editting = IAppSource.load(this, dataxName);
    File oldWorkDir = old.getDataXWorkDir(null);
    File edittingDir = editting.getDataXWorkDir(this);

    String edittingDirSuffix = StringUtils.substringAfter(edittingDir.getName(), oldWorkDir.getName());
    Matcher matcher = PatternEdittingDirSuffix.matcher(edittingDirSuffix);
    if (!matcher.matches()) {
      throw new IllegalStateException("dir name is illegal,oldDir:" + oldWorkDir.getAbsolutePath() + " editting dir:" + edittingDir.getAbsolutePath());
    }

    File backDir = new File(oldWorkDir.getParentFile(), oldWorkDir.getName() + ".bak");
    // 先备份
    try {
      FileUtils.moveDirectory(oldWorkDir, backDir);
      FileUtils.moveDirectory(edittingDir, oldWorkDir);
      FileUtils.forceDelete(backDir);
    } catch (Exception e) {
      try {
        FileUtils.moveDirectory(backDir, oldWorkDir);
      } catch (Throwable ex) {

      }
      throw new IllegalStateException("oldWorkDir update is illegal:" + oldWorkDir.getAbsolutePath(), e);
    }
    // 更新一下时间戳，workflow 会重新创建流程
    Application dataXApp = new Application();
    dataXApp.setUpdateTime(new Date());
    ApplicationCriteria appCriteria = new ApplicationCriteria();
    appCriteria.createCriteria().andProjectNameEqualTo(dataxName);
    this.getApplicationDAO().updateByExampleSelective(dataXApp, appCriteria);
    this.addActionMessage(context, "已经成功更新");
  }

  /**
   * 创建一个临时执行目录
   *
   * @param context
   * @throws Exception
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doCreateUpdateProcess(Context context) throws Exception {
    String dataXName = this.getCollectionName();
    String execId = this.getString("execId");
    if (StringUtils.isBlank(execId)) {
      throw new IllegalArgumentException("param execId can not be null");
    }
    DataxProcessor dataxProcessor = IAppSource.load(null, dataXName);
    dataxProcessor.makeTempDir(execId);
    // 创建临时执行目录
    this.setBizResult(context, execId);
  }

  /**
   * 保存表映射
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doSaveTableMapper(Context context) {
    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    // 表别名列表
    JSONArray tabAliasList = this.parseJsonArrayPost();
    Objects.requireNonNull(tabAliasList, "tabAliasList can not be null");

    JSONObject alias = null;
    IDataxProcessor.TableAlias tabAlias = null;
    List<IDataxProcessor.TableAlias> tableMaps = Lists.newArrayList();


    String mapperToVal = null;
    for (int i = 0; i < tabAliasList.size(); i++) {
      alias = tabAliasList.getJSONObject(i);
      tabAlias = new IDataxProcessor.TableAlias();
      tabAlias.setFrom(alias.getString("from"));
      mapperToVal = alias.getString("to");
      String mapper2FieldKey = "tabMapperTo[" + i + "]";
      if (Validator.require.validate(this, context, mapper2FieldKey, mapperToVal)) {
        Validator.db_col_name.validate(this, context, mapper2FieldKey, mapperToVal);
      }
      tabAlias.setTo(mapperToVal);
      tableMaps.add(tabAlias);
    }

    if (context.hasErrors()) {
      return;
    }

    this.saveTableMapper(this, dataxName, tableMaps);

  }

  private void saveTableMapper(IPluginContext pluginContext, String dataxName, List<IDataxProcessor.TableAlias> tableMaps) {

    if (StringUtils.isBlank(dataxName)) {
      throw new IllegalArgumentException("param dataxName can not be null");
    }

    DataxProcessor dataxProcessor = DataxProcessor.load(this, dataxName);//  appSource.isPresent() ? appSource.get() : (DataxProcessor) appSourceParseDescribable.instance;
    dataxProcessor.setTableMaps(tableMaps);
    IAppSource.save(pluginContext, dataxName, dataxProcessor);
  }

  /**
   * 取得表映射
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetTableMapper(Context context) {
    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    KeyedPluginStore<DataxReader> readerStore = DataxReader.getPluginStore(this, dataxName);
    DataxReader dataxReader = readerStore.getPlugin();
    Objects.requireNonNull(dataxReader, "dataReader:" + dataxName + " relevant instance can not be null");

    IDataxProcessor.TableAlias tableAlias;
    Optional<DataxProcessor> dataXAppSource = IAppSource.loadNullable(this, dataxName);
    Map<String, IDataxProcessor.TableAlias> tabMaps = Collections.emptyMap();
    if (dataXAppSource.isPresent()) {
      DataxProcessor dataxSource = dataXAppSource.get();
      tabMaps = dataxSource.getTabAlias();
    }

    if (!dataxReader.hasMulitTable()) {
      throw new IllegalStateException("reader has not set table at least");
    }
    List<IDataxProcessor.TableAlias> tmapList = Lists.newArrayList();
    for (ISelectedTab selectedTab : dataxReader.getSelectedTabs()) {
      tableAlias = tabMaps.get(selectedTab.getName());
      if (tableAlias == null) {
        tmapList.add(new IDataxProcessor.TableAlias(selectedTab.getName()));
      } else {
        tmapList.add(tableAlias);
      }
    }
    this.setBizResult(context, tmapList);

  }

  /**
   * 当reader为非RDBMS，writer为RDBMS类型时 需要为writer设置表名称，以及各个列的名称
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetWriterColsMeta(Context context) {
    final String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    DataxProcessor.DataXCreateProcessMeta processMeta = DataxProcessor.getDataXCreateProcessMeta(this, dataxName);
    DataxProcessor processor = DataxProcessor.load(this, dataxName);

    if (processMeta.isReaderRDBMS()) {
      throw new IllegalStateException("can not process the flow with:" + processMeta.toString());
    }
    int selectedTabsSize = processMeta.getReader().getSelectedTabs().size();
    if (selectedTabsSize != 1) {
      throw new IllegalStateException("dataX reader getSelectedTabs size must be 1 ,but now is :" + selectedTabsSize);
    }
    Map<String, IDataxProcessor.TableAlias> tabAlias = processor.getTabAlias();
    Optional<IDataxProcessor.TableAlias> findMapper = tabAlias.values().stream().findFirst();
    IDataxProcessor.TableMap tabMapper = null;
    for (ISelectedTab selectedTab : processMeta.getReader().getSelectedTabs()) {

      if (findMapper.isPresent()) {
        if (!(findMapper.get() instanceof IDataxProcessor.TableMap)) {
          throw new IllegalStateException("tableAlias must be type of " + IDataxProcessor.TableMap.class.getName());
        }
        tabMapper = (IDataxProcessor.TableMap) findMapper.get();
      } else {
        tabMapper = new IDataxProcessor.TableMap();
        tabMapper.setSourceCols(selectedTab.getCols());
        tabMapper.setFrom(selectedTab.getName());
        tabMapper.setTo(selectedTab.getName());
      }

      this.setBizResult(context, tabMapper);
      return;
    }
  }

  @Func(value = PermissionConstant.APP_ADD)
  public void doGotoEsAppCreateConfirm(Context context) throws Exception {
    this.errorsPageShow(context);
    // 这里只做schema的校验
    CreateIndexConfirmModel confiemModel = parseJsonPost(CreateIndexConfirmModel.class);
    String schemaContent = null;
    if (confiemModel.isExpertModel()) {
      schemaContent = confiemModel.getExpert().getXml();
    } else {

      ISearchEngineTypeTransfer typeTransfer = ISearchEngineTypeTransfer.load(this, confiemModel.getDataxName());
      schemaContent = typeTransfer.mergeFromStupidModel(confiemModel.getStupid().getModel()
        , ISearchEngineTypeTransfer.getOriginExpertSchema(null)).toJSONString();
    }

    ESTableAlias esTableAlias = new ESTableAlias();
    esTableAlias.setSchemaContent(schemaContent);

    this.saveTableMapper(this, confiemModel.getDataxName(), Collections.singletonList(esTableAlias));
  }

  /**
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE)
  public void doSaveWriterColsMeta(Context context) {
    String dataxName = this.getString(PARAM_KEY_DATAX_NAME);
    DataxProcessor.DataXCreateProcessMeta processMeta = DataxProcessor.getDataXCreateProcessMeta(this, dataxName);
    if ((processMeta.isReaderRDBMS())) {
      throw new IllegalStateException("can not process the flow with:" + processMeta.toString());
    }

    IDataxProcessor.TableMap tableMapper = new IDataxProcessor.TableMap();
    List<ISelectedTab.ColMeta> writerCols = Lists.newArrayList();

    tableMapper.setSourceCols(writerCols);
    ////////////////////
    final String keyColsMeta = "colsMeta";
    IControlMsgHandler handler = new DelegateControl4JsonPostMsgHandler(this, this.parseJsonPost());
    if (!Validator.validate(handler, context, Validator.fieldsValidator( //
      "writerTargetTabName" //
      , new Validator.FieldValidators(Validator.require, Validator.db_col_name) {
        @Override
        public void setFieldVal(String val) {
          tableMapper.setTo(val);
        }
      },
      "writerFromTabName"
      , new Validator.FieldValidators(Validator.require, Validator.db_col_name) {
        @Override
        public void setFieldVal(String val) {
          tableMapper.setFrom(val);
        }
      },
      keyColsMeta //
      , new Validator.FieldValidators(Validator.require) {
        @Override
        public void setFieldVal(String val) {
        }
      }
      , new Validator.IFieldValidator() {
        @Override
        public boolean validate(IFieldErrorHandler msgHandler, Context context, String fieldKey, String fieldData) {
          ISelectedTab.ColMeta colMeta = null;
          JSONArray targetCols = JSON.parseArray(fieldData);
          JSONObject targetCol = null;
          int index;
          String targetColName = null;

          if (targetCols.size() < 1) {
            msgHandler.addFieldError(context, fieldKey, "Writer目标表列不能为空");
            return false;
          }
          Map<String, Integer> existCols = Maps.newHashMap();
          boolean validateFaild = false;
          Integer previousColIndex = null;
          for (int i = 0; i < targetCols.size(); i++) {
            targetCol = targetCols.getJSONObject(i);
            index = targetCol.getInteger("index");
            targetColName = targetCol.getString("name");
            if (StringUtils.isNotBlank(targetColName) && (previousColIndex = existCols.put(targetColName, index)) != null) {
              msgHandler.addFieldError(context, keyColsMeta + "[" + previousColIndex + "]", "内容不能与第" + index + "行重复");
              msgHandler.addFieldError(context, keyColsMeta + "[" + index + "]", "内容不能与第" + previousColIndex + "行重复");
              return false;
            }
            if (!Validator.require.validate(DataxAction.this, context, keyColsMeta + "[" + index + "]", targetColName)) {
              validateFaild = true;
            } else if (!Validator.db_col_name.validate(DataxAction.this, context, keyColsMeta + "[" + index + "]", targetColName)) {
              validateFaild = true;
            }
            colMeta = new ISelectedTab.ColMeta();
            colMeta.setName(targetColName);

            ColumnMetaData.DataType dataType = targetCol.getObject("type", ColumnMetaData.DataType.class);
            // colMeta.setType(ISelectedTab.DataXReaderColType.parse(targetCol.getString("type")));
            colMeta.setType(dataType);
            writerCols.add(colMeta);
          }

          return !validateFaild;
        }
      }
    ))) {
      return;
    }


    this.saveTableMapper(this, dataxName, Collections.singletonList(tableMapper));
  }

  /**
   * submit reader type and writer type form for validate
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doValidateReaderWriter(Context context) {
    this.errorsPageShow(context);
    JSONObject post = this.parseJsonPost();

    String dataxPipeName = post.getString("dataxPipeName");

    JSONObject reader = post.getJSONObject("readerDescriptor");
    JSONObject writer = post.getJSONObject("writerDescriptor");
//    Objects.requireNonNull(reader, "reader can not be null");
//    Objects.requireNonNull(writer, "writer can not be null");
    if (reader == null || writer == null) {
      this.addErrorMessage(context, "请选择'Reader类型'和'Writer类型'");
      return;
    }
    DataxReader.BaseDataxReaderDescriptor readerDesc = (DataxReader.BaseDataxReaderDescriptor) TIS.get().getDescriptor(reader.getString("impl"));
    DataxWriter.BaseDataxWriterDescriptor writerDesc = (DataxWriter.BaseDataxWriterDescriptor) TIS.get().getDescriptor(writer.getString("impl"));
    DataXBasicProcessMeta processMeta = getDataXBasicProcessMeta(readerDesc, writerDesc);
    this.setBizResult(context, processMeta);
  }

  private DataXBasicProcessMeta getDataXBasicProcessMeta(DataxReader.BaseDataxReaderDescriptor readerDesc, DataxWriter.BaseDataxWriterDescriptor writerDesc) {
    Objects.requireNonNull(readerDesc, "readerDesc can not be null");
    Objects.requireNonNull(writerDesc, "writerDesc can not be null");

    DataXBasicProcessMeta processMeta = new DataXBasicProcessMeta();
    processMeta.setReaderHasExplicitTable(readerDesc.hasExplicitTable());
    processMeta.setReaderRDBMS(readerDesc.isRdbms());
    processMeta.setWriterRDBMS(writerDesc.isRdbms());
    processMeta.setWriterSupportMultiTableInReader(writerDesc.isSupportMultiTable());
    return processMeta;
  }

  /**
   * dataX创建之后的管理页面使用
   *
   * @param context
   */
  @Func(value = PermissionConstant.DATAX_MANAGE, sideEffect = false)
  public void doGetDataXMeta(Context context) {
    String dataXName = this.getCollectionName();

    DataxProcessor processor = IAppSource.load(this, dataXName);

    DataxReader reader = (DataxReader) processor.getReader(this);
    DataxWriter writer = (DataxWriter) processor.getWriter(this);
    DataxReader.BaseDataxReaderDescriptor readerDesc = (DataxReader.BaseDataxReaderDescriptor) reader.getDescriptor();
    DataxWriter.BaseDataxWriterDescriptor writerDesc = (DataxWriter.BaseDataxWriterDescriptor) writer.getDescriptor();

    DescriptorsJSON readerDescriptor = new DescriptorsJSON(readerDesc);
    DescriptorsJSON writerDescriptor = new DescriptorsJSON(writerDesc);
    Map<String, Object> result = Maps.newHashMap();
    result.put("processMeta", getDataXBasicProcessMeta(readerDesc, writerDesc));
    result.put("writerDesc", writerDescriptor.getDescriptorsJSON());
    result.put("readerDesc", readerDescriptor.getDescriptorsJSON());
    setBizResult(context, result);
  }

  public static List<String> getTablesInDB(IPropertyType.SubFormFilter filter) {

    String dataxName = filter.param(DataxUtils.DATAX_NAME);

    DataxReader reader = DataxReader.load(filter.uploadPluginMeta.getPluginContext(), dataxName);
//    KeyedPluginStore<DataxReader> readerStore = DataxReader.getPluginStore(filter.uploadPluginMeta.getPluginContext(), dataxName);
//   readerStore.getPlugin();
//    Objects.requireNonNull(reader, "reader can not be null");
    return reader.getTablesInDB();
  }

  public static List<ColumnMetaData> getReaderTableSelectableCols(String dataxName, String table) {
//    KeyedPluginStore<DataxReader> readerStore = DataxReader.getPluginStore(dataxName);
//    DataxReader reader = readerStore.getPlugin();
//    Objects.requireNonNull(reader, "reader can not be null");
//    List<ColumnMetaData> tableMeta = reader.getTableMetadata(table);
//    return tableMeta;

    throw new UnsupportedOperationException();
  }

//  /**
//   * get cols meta
//   *
//   * @param context
//   */
//  public void doGetReaderTableSelectableCols(Context context) {
//    String dataxName = this.getString(DataxUtils.DATAX_NAME);
//    String tableName = this.getString("tableName");
//    KeyedPluginStore<DataxReader> readerStore = DataxReader.getPluginStore(dataxName);
//    DataxReader reader = readerStore.getPlugin();
//    Objects.requireNonNull(reader, "reader can not be null");
//    List<ColumnMetaData> tableMeta = reader.getTableMetadata(tableName);
//    this.setBizResult(context, tableMeta);
//  }


  public static class DataxPluginDescMeta extends PluginDescMeta<DataxReader> {
    private final DescriptorsJSON writerTypesDesc;

    public DataxPluginDescMeta(DescriptorExtensionList<DataxReader, Descriptor<DataxReader>> readerTypes
      , DescriptorExtensionList<DataxWriter, Descriptor<DataxWriter>> writerTypes) {
      super(readerTypes);
      this.writerTypesDesc = new DescriptorsJSON(writerTypes);
    }

    @JSONField(serialize = false)
    public com.alibaba.fastjson.JSONObject getPluginDesc() {
      throw new UnsupportedOperationException();
    }

    public com.alibaba.fastjson.JSONObject getReaderDesc() {
      return pluginDesc.getDescriptorsJSON();
    }


    public com.alibaba.fastjson.JSONObject getWriterDesc() {
      return writerTypesDesc.getDescriptorsJSON();
    }
  }

}
