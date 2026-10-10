package com.surexu.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.surexu.sesame.data.ConfigV2;
import com.surexu.sesame.data.ModelFields;

import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.modelFieldExt.SelectModelField;
import com.surexu.sesame.data.modelFieldExt.StringModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.entity.AlipayAntMemberTaskList;
import com.surexu.sesame.entity.AlipayWelfareFundTaskList;
import com.surexu.sesame.entity.AlipayMemberCreditSesameTaskList;
import com.surexu.sesame.entity.MemberBenefit;
import com.surexu.sesame.hook.ApplicationHook;
import com.surexu.sesame.model.base.TaskCommon;
import com.surexu.sesame.model.base.TaskAlternative;
import com.surexu.sesame.model.extensions.ExtensionsHandle;
import com.surexu.sesame.model.task.antOrchard.AntOrchard;
import com.surexu.sesame.model.task.antOrchard.AntOrchardRpcCall;
import com.surexu.sesame.util.*;
import com.surexu.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import com.surexu.sesame.util.idMap.AntMemberTaskListMap;
import com.surexu.sesame.util.idMap.MemberBenefitIdMap;
import com.surexu.sesame.util.idMap.MemberCreditSesameTaskListMap;
import com.surexu.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import com.surexu.sesame.util.idMap.UserIdMap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AntMember extends ModelTask {
    private static final String TAG = AntMember.class.getSimpleName();

    /**
     * `doFarmTask` 已发出、但响应不足以判定成败的游戏中心任务：{@code taskId -> 任务标题}。
     * <p>由 {@link #verifyPendingTasks()} 在列表处理完后按任务列表状态核对。
     */
    private final Map<String, String> pendingVerifyTasks = new LinkedHashMap<>();

    /** 同轮核对配置（见 TaskAlternative.verify） */
    private static final TaskAlternative.VerifyConfig VERIFY_CFG = new TaskAlternative.VerifyConfig(
            "AntMember", "AntMemberTaskList", "会员任务", "游戏中心", "🎮完成", true, msg -> Log.other(msg));

    /** 游戏中心宝箱每日上限（首页 {@code taskModule.dailyTaskCertCntUpperLimit} 的兜底值）。 */
    private static final int GAME_CENTER_CERT_LIMIT = 30;
    
    @Override
    public String getName() {
        return "会员";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.MEMBER;
    }
    
    private BooleanModelField AntMemberTask;
    private BooleanModelField AutoAntMemberTaskList;
    private SelectModelField AntMemberTaskList;
    private BooleanModelField memberSign;
    private BooleanModelField memberPointExchangeBenefit;
    private SelectModelField memberPointExchangeBenefitList;
    private BooleanModelField memberPointExchangeSecKill;
    private StringModelField memberPointExchangeSecKillTimes;
    private StringModelField memberPointExchangeCustom;
    
    private BooleanModelField collectSesame;
    private BooleanModelField AutoMemberCreditSesameTaskList;
    private SelectModelField MemberCreditSesameTaskList;
    private BooleanModelField SesameGrowthBehavior;
    private BooleanModelField promise;
    private SelectModelField promiseList;
    private BooleanModelField enableGameCenter;
    private BooleanModelField enableGoldTicket;
    private BooleanModelField enableGoldTicketConsume;
    private BooleanModelField KuaiDiFuLiJia;
    private BooleanModelField welfareFund;
    private BooleanModelField welfareFundSign;
    private BooleanModelField welfareFundTask;
    private BooleanModelField AutoWelfareFundTaskList;
    private SelectModelField WelfareFundTaskList;
    private BooleanModelField merchantSignIn;
    private BooleanModelField merchantKMDK;
    private BooleanModelField alchemyTask;
    private BooleanModelField insBeanSignIn;
    private BooleanModelField insBeanExchangeBubbleBoost;
    private BooleanModelField insBeanExchangeGoldenTicket;
    private BooleanModelField insGainSumInsured;

    /** 额外兑换名单（整点秒杀到点自动抢的目标） */
    public StringModelField getMemberPointExchangeCustom() {
        return memberPointExchangeCustom;
    }

    /** 整点秒杀开关 */
    public BooleanModelField getMemberPointExchangeSecKill() {
        return memberPointExchangeSecKill;
    }

    /** 秒杀时间点（逗号分隔 HH:mm） */
    public StringModelField getMemberPointExchangeSecKillTimes() {
        return memberPointExchangeSecKillTimes;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(AntMemberTask = new BooleanModelField("AntMemberTask", "会员任务", false));
        modelFields.addField(AutoAntMemberTaskList = new BooleanModelField("AutoAntMemberTaskList", "会员任务 | 自动黑名单", true).setDependsOn("AntMemberTask"));
        modelFields.addField(AntMemberTaskList = new SelectModelField("AntMemberTaskList", "会员任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntMemberTaskList::getList).setDependsOn("AutoAntMemberTaskList"));
        modelFields.addField(memberSign = new BooleanModelField("memberSign", "会员签到", false));
        modelFields.addField(memberPointExchangeBenefit = new BooleanModelField("memberPointExchangeBenefit", "会员积分 | 兑换权益", false));
        modelFields.addField(memberPointExchangeBenefitList = new SelectModelField("memberPointExchangeBenefitList", "会员积分 | 权益列表", new LinkedHashSet<>(), MemberBenefit::getList).setDependsOn("memberPointExchangeBenefit"));
        modelFields.addField(memberPointExchangeSecKill = new BooleanModelField("memberPointExchangeSecKill", "会员积分 | 整点秒杀", false));
        modelFields.addField(memberPointExchangeSecKillTimes = new StringModelField("memberPointExchangeSecKillTimes", "会员积分 | 秒杀时间点", "10:00,20:00").setDependsOn("memberPointExchangeSecKill"));
        modelFields.addField(memberPointExchangeCustom = new StringModelField("memberPointExchangeCustom", "会员积分 | 额外兑换(名称)", ""));
        modelFields.addField(collectSesame = new BooleanModelField("collectSesame", "芝麻粒 | 领取", false));
        modelFields.addField(AutoMemberCreditSesameTaskList = new BooleanModelField("AutoMemberCreditSesameTaskList", "芝麻粒任务 | 自动黑名单", true).setDependsOn("collectSesame"));
        modelFields.addField(MemberCreditSesameTaskList = new SelectModelField("MemberCreditSesameTaskList", "芝麻粒任务 | 黑名单列表", new LinkedHashSet<>(), AlipayMemberCreditSesameTaskList::getList).setDependsOn("AutoMemberCreditSesameTaskList"));
        modelFields.addField(SesameGrowthBehavior = new BooleanModelField("SesameGrowthBehavior", "攒芝麻分进度", false));
        modelFields.addField(enableGameCenter = new BooleanModelField("enableGameCenter", "游戏中心 | 得乐园豆", false));
        //modelFields.addField(promise = new BooleanModelField("promise", "生活记录 | 坚持做", false));
        //modelFields.addField(promiseList = new SelectModelField("promiseList", "生活记录 | 坚持做列表", new LinkedHashSet<>(), PromiseSimpleTemplate::getList));
        modelFields.addField(KuaiDiFuLiJia = new BooleanModelField("KuaiDiFuLiJia", "我的快递 | 福利加", false));
        modelFields.addField(enableGoldTicket = new BooleanModelField("enableGoldTicket", "黄金票 | 签到与收取", false));
        modelFields.addField(enableGoldTicketConsume = new BooleanModelField("enableGoldTicketConsume", "黄金票 | 提取/兑换黄金", false));
        modelFields.addField(merchantSignIn = new BooleanModelField("merchantSignIn", "商家服务 | 签到", false));
        modelFields.addField(merchantKMDK = new BooleanModelField("merchantKMDK", "商家服务 | 开门打卡", false));
        modelFields.addField(alchemyTask = new BooleanModelField("alchemyTask", "芝麻炼金", false));
        modelFields.addField(insBeanSignIn = new BooleanModelField("insBeanSignIn", "蚂蚁保障 | 安心豆签到", false));
        modelFields.addField(insBeanExchangeBubbleBoost = new BooleanModelField("insBeanExchangeBubbleBoost", "蚂蚁保障 | 安心豆兑换时光加速器", false));
        modelFields.addField(insBeanExchangeGoldenTicket = new BooleanModelField("insBeanExchangeGoldenTicket", "蚂蚁保障 | 安心豆兑换黄金票", false));
        modelFields.addField(insGainSumInsured = new BooleanModelField("insGainSumInsured", "蚂蚁保障 | 保障金领取", false));
        modelFields.addField(welfareFund = new BooleanModelField("welfareFund", "福利金 | 开启", false));
        modelFields.addField(welfareFundSign = new BooleanModelField("welfareFundSign", "福利金 | 签到", true).setDependsOn("welfareFund"));
        modelFields.addField(welfareFundTask = new BooleanModelField("welfareFundTask", "福利金 | 任务", true).setDependsOn("welfareFund"));
        modelFields.addField(AutoWelfareFundTaskList = new BooleanModelField("AutoWelfareFundTaskList", "福利金任务 | 自动黑名单", true).setDependsOn("welfareFundTask"));
        modelFields.addField(WelfareFundTaskList = new SelectModelField("WelfareFundTaskList", "福利金任务 | 黑名单列表", new LinkedHashSet<>(), AlipayWelfareFundTaskList::getList).setDependsOn("AutoWelfareFundTaskList"));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️蚂蚁会员:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initMember")) {
                initMemberTaskListMap(AutoAntMemberTaskList.getValue(), AutoMemberCreditSesameTaskList.getValue(), AntMemberTask.getValue(), collectSesame.getValue());
                Status.flagToday("BlackList::initMember");
            }
            
            if (memberSign.getValue()) {
                memberSign();
            }
            
            if (AntMemberTask.getValue()) {
                queryPointCert(1, 8);
                signPageTaskList();
                queryAllStatusTaskList();
            }
            
            memberPointExchangeBenefit();
            AntMemberExchange.scheduleSecKill(this, memberPointExchangeSecKillTimes.getValue(), memberPointExchangeSecKill.getValue());
            if (collectSesame.getValue()) {
                CheckInTaskRpcManager();
                collectSesame();
            }
            
            //芝麻炼金
            if (alchemyTask.getValue()) {
                doSesameAlchemy();
            }
            //蚂蚁保障
            if (insBeanSignIn.getValue() || insBeanExchangeBubbleBoost.getValue() || insBeanExchangeGoldenTicket.getValue() || insGainSumInsured.getValue()) {
                Set<String> insuranceTasks = new HashSet<>();
                if (insBeanSignIn.getValue()) {
                    insuranceTasks.add("beanSignIn");
                }
                if (insBeanExchangeBubbleBoost.getValue()) {
                    insuranceTasks.add("beanExchangeBubbleBoost");
                }
                if (insBeanExchangeGoldenTicket.getValue()) {
                    insuranceTasks.add("beanExchangeGoldenTicket");
                }
                if (insGainSumInsured.getValue()) {
                    insuranceTasks.add("gainSumInsured");
                }
                AntInsurance.executeTask(insuranceTasks);
            }
            
            //芝麻积攒进度
            if (SesameGrowthBehavior.getValue()) {
                handleGrowthGuideTasks();
                queryAndCollect();
            }
            // 我的快递任务
            if (KuaiDiFuLiJia.getValue()) {
                RecommendTask();
                OrdinaryTask();
            }
            if (enableGoldTicket.getValue() || enableGoldTicketConsume.getValue()) {
                goldTicket();
            }
            if (enableGameCenter.getValue()) {
                //检查并执行签到
                checkAndDoSignIn();
                //查询并处理任务列表
                queryAndProcessTaskList();
                //游戏中心任务奖励（整场景一键领取；游戏类任务需真实游玩才能推进，不做完成尝试）
                gameCenterTaskPrize();

                //查询玩乐豆小球列表，有则领取
                queryPointBallList();

                if (merchantSignIn.getValue() || merchantKMDK.getValue()) {
                    if (MerchantService.transcodeCheck()) {
                        if (merchantSignIn.getValue()) {
                            MerchantService.taskListQueryV2();
                        }
                        if (merchantKMDK.getValue()) {
                            MerchantService.merchantKMDK();
                        }
                    }
                }
            }
            // 网商银行福利金（余额/签到/任务）
            if (welfareFund.getValue()) {
                WelfareFund.run(welfareFundSign.getValue(), welfareFundTask.getValue(),
                        AutoWelfareFundTaskList.getValue(), WelfareFundTaskList.getValue());
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static void initMemberTaskListMap(boolean AutoAntMemberTaskList, boolean AutoMemberCreditSesameTaskList, boolean AntMemberTask, boolean collectSesame) {
        try {
            //初始化AntMemberTaskListMap
            AntMemberTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            // 可继续添加更多黑名单任务
            
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛芝麻树");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntMemberTaskListMap.add(task, task);
            }
            
            JSONObject jo;
            if (AntMemberTask) {
                boolean hasNextPage = true;
                int page = 1;
                do {
                    jo = new JSONObject(AntMemberRpcCall.queryPointCert(page, 8));
                    TimeUtil.sleep(500);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        break;
                    }
                    hasNextPage = jo.getBoolean("hasNextPage");
                    page++;
                    JSONArray jaCertList = jo.getJSONArray("certList");
                    for (int i = 0; i < jaCertList.length(); i++) {
                        jo = jaCertList.getJSONObject(i);
                        String bizTitle = jo.getString("bizTitle");
                        AntMemberTaskListMap.add(bizTitle, bizTitle);
                    }
                }
                while (hasNextPage);
                
                jo = new JSONObject(AntMemberRpcCall.queryAllStatusTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray availableTaskList = jo.getJSONArray("availableTaskList");
                    for (int i = 0; i < availableTaskList.length(); i++) {
                        JSONObject task = availableTaskList.getJSONObject(i);
                        JSONObject taskConfigInfo = task.getJSONObject("taskConfigInfo");
                        String name = taskConfigInfo.getString("name");
                        AntMemberTaskListMap.add(name, name);
                    }
                    JSONArray taskHistoryList = jo.getJSONArray("taskHistoryList");
                    for (int i = 0; i < taskHistoryList.length(); i++) {
                        JSONObject task = taskHistoryList.getJSONObject(i);
                        JSONObject taskConfigInfo = task.getJSONObject("taskConfigInfo");
                        String name = taskConfigInfo.getString("name");
                        AntMemberTaskListMap.add(name, name);
                    }
                }
                
                // 游戏中心任务流里的任务也要进候选，否则用户看不到、也无法手动勾选
                for (int feedPage = 0; feedPage < 5; feedPage++) {
                    jo = new JSONObject(AntMemberRpcCall.gameCenterGameFeeds(feedPage, 10));
                    if (!MessageUtil.checkSuccess(TAG, jo)) {
                        break;
                    }
                    JSONObject feedsData = jo.optJSONObject("data");
                    JSONArray feedsList = feedsData == null ? null : feedsData.optJSONArray("feedsList");
                    if (feedsList == null || feedsList.length() == 0) {
                        break;
                    }
                    for (int i = 0; i < feedsList.length(); i++) {
                        JSONObject feed = feedsList.optJSONObject(i);
                        String title = feed == null ? "" : gameCenterTaskTitle(feed);
                        if (!title.isEmpty()) {
                            AntMemberTaskListMap.add(title, title);
                        }
                    }
                    if (!feedsData.optBoolean("haveNextPage", false)) {
                        break;
                    }
                }
                
                //保存任务到配置文件
                AntMemberTaskListMap.save();
                Log.record("同步任务🉑会员任务列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntMemberTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields antMember = config.getModelFieldsMap().get("AntMember");
                    SelectModelField AntMemberTaskList = (SelectModelField) antMember.get("AntMemberTaskList");
                    if (AntMemberTaskList == null) {
                        return;
                    }
                    
                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("会员任务", "AntMemberTaskList", blackList, whiteList, AntMemberTaskList);
                }
            }
            //初始化MemberCreditSesameTaskListMap
            MemberCreditSesameTaskListMap.load();
            blackList = new HashSet<>();
            // 实测（2026-09-22 抓包 logs/chk_sesame3）：芝麻粒任务走 taskFeedback 后服务端**不校验是否真的参与过**，
            // 未报名的「去玩xx」一次即 success ⇒ 游戏/浏览/签到/组件/施肥类不再预置拉黑，全部交给任务循环自动完成。
            // 仍预置拉黑的只剩**真实交易/履约类**（下单/租赁/订酒店/回收/雇佣/付钱/查车），
            // 这类没真做就申报"完成"属虚假履约，有风控风险
            blackList.add("用额度免押金下单");
            blackList.add("去租赁下单");
            blackList.add("芝麻租赁下单得芝麻粒");
            blackList.add("去飞猪订酒店");
            blackList.add("0.1元起租会员攒粒");
            blackList.add("9.9元抢租3天大疆");
            blackList.add("1分起囤神券茶咖美食");
            blackList.add("完成旧衣回收得现金");
            blackList.add("去雇佣芝麻大表鸽");
            blackList.add("送你10.6元支付红包");
            blackList.add("一键查询爱车估值");
            // 可继续添加更多黑名单任务
            
            whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("逛一逛芝麻树");
            whiteList.add("浏览15秒视频广告");
            whiteList.add("逛15秒商品橱窗");
            whiteList.add("逛一逛集汗滴找现金");
            whiteList.add("去体验先用后付");
            whiteList.add("去抛竿钓鱼");
            whiteList.add("去参与花呗活动");
            whiteList.add("坚持攒保障金");
            whiteList.add("去领支付宝积分");
            whiteList.add("去浏览租赁大促会场");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                MemberCreditSesameTaskListMap.add(task, task);
            }
            
            if (collectSesame) {
                jo = new JSONObject(AntMemberRpcCall.queryHome());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONObject entrance = jo.getJSONObject("entrance");
                    if (entrance.optBoolean("openApp")) {
                        jo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            if (jo.has("data")) {
                                JSONObject data = jo.getJSONObject("data");
                                if (data.has("completeVOS")) {
                                    JSONArray completeVOS = data.getJSONArray("completeVOS");
                                    for (int i = 0; i < completeVOS.length(); i++) {
                                        JSONObject toCompleteVO = completeVOS.getJSONObject(i);
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                                if (data.has("toCompleteVOS")) {
                                    JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
                                    for (int i = 0; i < toCompleteVOS.length(); i++) {
                                        JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                            }
                        }
                    }
                }
                //保存任务到配置文件
                MemberCreditSesameTaskListMap.save();
                Log.record("同步任务🉑会员芝麻信用任务芝麻粒列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoMemberCreditSesameTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields antMember = config.getModelFieldsMap().get("AntMember");
                    SelectModelField MemberCreditSesameTaskList = (SelectModelField) antMember.get("MemberCreditSesameTaskList");
                    if (MemberCreditSesameTaskList == null) {
                        return;
                    }
                    
                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("会员芝麻信用任务芝麻粒", "MemberCreditSesameTaskList", blackList, whiteList, MemberCreditSesameTaskList);
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "initMemberTaskListMap err:", t);
        }
    }
    
    private void memberSign() {
        try {
            if (!Status.hasFlagToday("member::sign")) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryMemberSigninCalendar());
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    if (jo.getBoolean("autoSignInSuccess")) {
                        Log.other("会员任务📅签到[坚持" + jo.getString("signinSumDay") + "天]#获得[" + jo.getString("signinPoint") + "积分]");
                    }
                    Status.flagToday("member::sign");
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "memberSign err:", t);
        }
    }
    
    private void queryPointCert(int page, int pageSize) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryPointCert(page, pageSize));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryPointCert page=" + page + " 接口返回失败");
                return;
            }
            boolean hasNextPage = jo.getBoolean("hasNextPage");
            JSONArray jaCertList = jo.getJSONArray("certList");
            Log.i(TAG, "queryPointCert page=" + page + " certList.size=" + jaCertList.length() + " hasNextPage=" + hasNextPage);
            for (int i = 0; i < jaCertList.length(); i++) {
                jo = jaCertList.getJSONObject(i);
                String bizTitle = jo.getString("bizTitle");
                //黑名单任务跳过
                if (AntMemberTaskList.getValue().contains(bizTitle)) {
                    continue;
                }
                String id = jo.getString("id");
                int pointAmount = jo.getInt("pointAmount");
                jo = new JSONObject(AntMemberRpcCall.receivePointByUser(id));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.other("会员任务🎖️领取[" + bizTitle + "]奖励#获得[" + pointAmount + "积分]");
                } else {
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", bizTitle, jo);
                }
            }
            if (hasNextPage) {
                queryPointCert(page + 1, pageSize);
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryPointCert err:", t);
        }
    }
    
    /**
     * 做任务赚积分
     */
    private void signPageTaskList() {
        try {
            do {
                String rawJson = AntMemberRpcCall.signPageTaskList();
                JSONObject jo = new JSONObject(rawJson);
                TimeUtil.sleep(500);
                boolean doubleCheck = false;
                if (!MessageUtil.checkResultCode(TAG + " signPageTaskList", jo)) {
                    return;
                }
                if (!jo.has("categoryTaskList")) {
                    Log.i(TAG, "signPageTaskList 无 categoryTaskList 字段");
                    return;
                }
                JSONArray categoryTaskList = jo.getJSONArray("categoryTaskList");
                for (int i = 0; i < categoryTaskList.length(); i++) {
                    jo = categoryTaskList.getJSONObject(i);
                    JSONArray taskList = jo.getJSONArray("taskList");
                    String type = jo.getString("type");
                    if (Objects.equals("BROWSE", type)) {
                        doubleCheck = doBrowseTask(taskList);
                    }
                    else {
                        ExtensionsHandle.handleAlphaRequest("antMember", "doMoreTask", jo);
                    }
                }
                if (doubleCheck) {
                    continue;
                }
                break;
            }
            while (true);
        }
        catch (Throwable t) {
            Log.err(TAG, "signPageTaskList err:", t);
        }
    }
    
    /**
     * 查询所有状态任务列表
     */
    private void queryAllStatusTaskList() {
        try {
            String rawJson = AntMemberRpcCall.queryAllStatusTaskList();
            JSONObject jo = new JSONObject(rawJson);
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryAllStatusTaskList 接口返回失败");
                return;
            }
            JSONArray availableTaskList = jo.getJSONArray("availableTaskList");
            if (doBrowseTask(availableTaskList)) {
                queryAllStatusTaskList();
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryAllStatusTaskList err:", t);
        }
    }
    
    // 生活记录
    private void promise() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.promiseQueryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            JSONArray promiseSimpleTemplates = jo.getJSONArray("promiseSimpleTemplates");
            for (int i = 0; i < promiseSimpleTemplates.length(); i++) {
                jo = promiseSimpleTemplates.getJSONObject(i);
                String templateId = jo.getString("templateId");
                String promiseName = jo.getString("promiseName");
                String status = jo.getString("status");
                if ("un_join".equals(status) && promiseList.getValue().contains(templateId)) {
                    promiseJoin(querySingleTemplate(templateId));
                }
                PromiseSimpleTemplateIdMap.add(templateId, promiseName);
            }
            PromiseSimpleTemplateIdMap.save(UserIdMap.getCurrentUid());
        }
        catch (Throwable t) {
            Log.err(TAG, "promise err:", t);
        }
    }
    
    private JSONObject querySingleTemplate(String templateId) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.querySingleTemplate(templateId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            jo = jo.getJSONObject("data");
            JSONObject result = new JSONObject();
            
            result.put("joinFromOuter", false);
            result.put("templateId", jo.getString("templateId"));
            result.put("autoRenewStatus", Boolean.valueOf(jo.getString("autoRenewStatus")));
            
            JSONObject joinGuarantyRule = jo.getJSONObject("joinGuarantyRule");
            joinGuarantyRule.put("selectValue", joinGuarantyRule.getJSONArray("canSelectValues").getString(0));
            joinGuarantyRule.remove("canSelectValues");
            result.put("joinGuarantyRule", joinGuarantyRule);
            
            JSONObject joinRule = jo.getJSONObject("joinRule");
            joinRule.put("selectValue", joinRule.getJSONArray("canSelectValues").getString(0));
            joinRule.remove("joinRule");
            result.put("joinRule", joinRule);
            
            JSONObject periodTargetRule = jo.getJSONObject("periodTargetRule");
            periodTargetRule.put("selectValue", periodTargetRule.getJSONArray("canSelectValues").getString(0));
            periodTargetRule.remove("canSelectValues");
            result.put("periodTargetRule", periodTargetRule);
            
            JSONObject dataSourceRule = jo.getJSONObject("dataSourceRule");
            dataSourceRule.put("selectValue", dataSourceRule.getJSONArray("canSelectValues").getJSONObject(0).getString("merchantId"));
            dataSourceRule.remove("canSelectValues");
            result.put("dataSourceRule", dataSourceRule);
            return result;
        }
        catch (Throwable t) {
            Log.err(TAG, "querySingleTemplate err:", t);
        }
        return null;
    }
    
    private void promiseJoin(JSONObject data) {
        if (data == null) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.promiseJoin(data));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            String promiseName = jo.getString("promiseName");
            Log.other("生活记录📝加入[" + promiseName + "]");
        }
        catch (Throwable t) {
            Log.err(TAG, "promiseJoin err:", t);
        }
    }
    
    // 查询持续做明细任务
    private JSONObject promiseQueryDetail(String recordId) throws JSONException {
        JSONObject jo = new JSONObject(AntMemberRpcCall.promiseQueryDetail(recordId));
        if (!jo.optBoolean("success")) {
            return null;
        }
        return jo;
    }
    
    // 蚂蚁积分-做浏览任务
    private Boolean doBrowseTask(JSONArray taskList) {
        boolean doubleCheck = false;
        try {
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.getJSONObject(i);
                if (task.getBoolean("hybrid")) {
                    int periodCurrentCount = Integer.parseInt(task.getJSONObject("extInfo").getString("PERIOD_CURRENT_COUNT"));
                    int periodTargetCount = Integer.parseInt(task.getJSONObject("extInfo").getString("PERIOD_TARGET_COUNT"));
                    int count = periodTargetCount > periodCurrentCount ? periodTargetCount - periodCurrentCount : 0;
                    if (count > 0) {
                        doubleCheck = doubleCheck || doBrowseTask(task, periodTargetCount, periodTargetCount);
                    }
                }
                else {
                    doubleCheck = doubleCheck || doBrowseTask(task, 1, 1);
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "doBrowseTask err:", t);
        }
        return doubleCheck;
    }
    
    private Boolean doBrowseTask(JSONObject task, int left, int right) {
        boolean doubleCheck = false;
        try {
            JSONObject taskConfigInfo = task.getJSONObject("taskConfigInfo");
            String name = taskConfigInfo.getString("name");
            //黑名单任务跳过
            if (AntMemberTaskList.getValue().contains(name)) {
                return false;
            }
            Long id = taskConfigInfo.getLong("id");
            String awardParamPoint = taskConfigInfo.getJSONObject("awardParam").getString("awardParamPoint");
            JSONArray targetBusinessArr = taskConfigInfo.optJSONArray("targetBusiness");
            if (targetBusinessArr == null || targetBusinessArr.length() == 0) {
                Log.other("会员任务⏭️跳过[" + name + "]#无 targetBusiness 配置");
                return false;
            }
            String targetBusiness = targetBusinessArr.getString(0);
            for (int i = left; i <= right; i++) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.applyTask(name, id));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", name, jo);
                    continue;
                }
                String[] targetBusinessArray = targetBusiness.split("#");
                String bizParam;
                String bizSubType;
                if (targetBusinessArray.length > 2) {
                    bizParam = targetBusinessArray[2];
                    bizSubType = targetBusinessArray[1];
                }
                else {
                    bizParam = targetBusinessArray[1];
                    bizSubType = targetBusinessArray[0];
                }
                jo = new JSONObject(AntMemberRpcCall.executeTask(bizParam, bizSubType));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", name, jo);
                    continue;
                }
                String ex = left == right && left == 1 ? "" : "(" + (i + 1) + "/" + right + ")";
                Log.other("会员任务🎖️完成[" + name + ex + "]#获得[" + awardParamPoint + "积分]");
                doubleCheck = true;
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "doBrowseTask err:", t);
        }
        return doubleCheck;
    }
    
    private void goldTicket() {
        try {
            boolean doSignIn = enableGoldTicket.getValue();
            boolean doConsume = enableGoldTicketConsume.getValue();
            if (!doSignIn && !doConsume) {
                return;
            }
            boolean needSignIn = doSignIn && !Status.hasFlagToday("goldTicket::sign");
            boolean needHomeCheck = doSignIn && !Status.hasFlagToday("goldTicket::home");
            boolean needWelfare = doSignIn && !Status.hasFlagToday("goldTicket::welfare");
            boolean needConsume = doConsume && !Status.hasFlagToday("goldTicket::consume");
            if (!needSignIn && !needHomeCheck && !needWelfare && !needConsume) {
                Log.i("黄金票🙈[今日已处理，跳过]");
                return;
            }
            Log.i("黄金票🙈[开始执行]");
            JSONObject home = null;
            if (needSignIn || needHomeCheck) {
                home = queryGoldTicketHome();
            }
            if (needSignIn) {
                if (home == null) {
                    Log.error("黄金票🙈[首页查询失败]无法判断签到状态");
                } else if (doGoldTicketSignIn(home)) {
                    Status.flagToday("goldTicket::sign");
                }
            }
            if (needHomeCheck) {
                if (home == null) {
                    Log.error("黄金票🙈[首页查询失败]跳过收取与任务扫描");
                } else {
                    doGoldTicketCollect(home);
                    if (handleGoldTicketHomeTasks(home)) {
                        Status.flagToday("goldTicket::home");
                    }
                }
            }
            if (needWelfare) {
                if (handleGoldTicketWelfareTasks()) {
                    Status.flagToday("goldTicket::welfare");
                }
            }
            if (needConsume) {
                doGoldTicketConsume();
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 黄金票系接口只返回 success:true（无 desc / resultCode），而 checkResultCode 要求 desc="处理成功"，
     * 会把这些响应全部误判为失败；故这里以 success/isSuccess 为准，再回退通用判定。
     */
    private static boolean goldTicketOk(String tag, JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if (jo.optBoolean("success") || jo.optBoolean("isSuccess")) {
            return true;
        }
        return MessageUtil.checkResultCode(tag, jo);
    }

    private JSONObject queryGoldTicketHome() {
        try {
            String res = AntMemberRpcCall.queryGoldTicketHome();
            if (res == null || res.isEmpty()) {
                return null;
            }
            JSONObject jo = new JSONObject(res);
            if (!goldTicketOk(TAG, jo)) {
                return null;
            }
            return jo;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    private JSONObject getGoldTicketAssetInfo(JSONObject home) {
        if (home == null) {
            return null;
        }
        // 首页响应在 result.upsertData.assetInfo 下，旧的 result.assetInfo 作兜底
        JSONObject asset = home.optJSONObject("assetInfo");
        if (asset == null) {
            JSONObject result = home.optJSONObject("result");
            if (result != null) {
                JSONObject upsertData = result.optJSONObject("upsertData");
                if (upsertData != null) {
                    asset = upsertData.optJSONObject("assetInfo");
                }
                if (asset == null) {
                    asset = result.optJSONObject("assetInfo");
                }
            }
        }
        return asset;
    }

    private boolean doGoldTicketSignIn(JSONObject home) {
        try {
            JSONObject assetInfo = getGoldTicketAssetInfo(home);
            boolean canSign = assetInfo != null && assetInfo.optBoolean("canSign", false);
            if (!canSign) {
                Log.i("黄金票🙈[今日已签到]");
                return true;
            }
            Log.i("黄金票🙈[准备签到]");
            boolean signSuccess = false;
            int collectCount = doGoldTicketIndexCollect("签到尝试");
            JSONObject refreshed = queryGoldTicketHome();
            if (refreshed != null) {
                JSONObject ra = getGoldTicketAssetInfo(refreshed);
                boolean stillCanSign = ra != null && ra.optBoolean("canSign", false);
                if (!stillCanSign) {
                    refreshGoldTicketWelfareCenter("首页收取签到");
                    Log.other(collectCount > 0 ? "黄金票🙈[签到成功]#通过首页收取完成签到" : "黄金票🙈[签到成功]");
                    signSuccess = true;
                }
            }
            if (!signSuccess) {
                String signRes = AntMemberRpcCall.welfareCenterTrigger("SIGN");
                if (signRes != null && !signRes.isEmpty()) {
                    JSONObject signJson = new JSONObject(signRes);
                    if (goldTicketOk(TAG, signJson)) {
                        JSONObject signResult = signJson.optJSONObject("result");
                        String amount = "";
                        if (signResult != null) {
                            JSONObject prize = signResult.optJSONObject("prize");
                            if (prize != null) {
                                amount = prize.optString("amount");
                            }
                        }
                        refreshGoldTicketWelfareCenter("签到");
                        JSONObject refreshed2 = queryGoldTicketHome();
                        JSONObject ra2 = refreshed2 != null ? getGoldTicketAssetInfo(refreshed2) : null;
                        signSuccess = refreshed2 != null && (ra2 == null || !ra2.optBoolean("canSign", false));
                        if (signSuccess || (amount != null && !amount.isEmpty())) {
                            Log.other(amount != null && !amount.isEmpty()
                                    ? "黄金票🙈[签到成功]#获得[" + amount + "]" : "黄金票🙈[签到成功]");
                            signSuccess = true;
                        }
                    }
                }
            }
            if (!signSuccess) {
                Log.error("黄金票🙈[签到失败]未找到可用签到返回");
            }
            return signSuccess;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private int doGoldTicketIndexCollect(String source) {
        String needleResponse = AntMemberRpcCall.goldTicketIndexCollect();
        if (needleResponse != null && !needleResponse.isEmpty()) {
            return logGoldTicketCollectResponse(needleResponse, source);
        }
        return logGoldTicketCollectResponse(AntMemberRpcCall.goldBillCollect(), source + "-旧版兼容");
    }

    private boolean refreshGoldTicketWelfareCenter(String source) {
        try {
            String updateResponse = AntMemberRpcCall.welfareCenterUpdate(9);
            if (updateResponse == null || updateResponse.isEmpty()) {
                Log.error("黄金票🙈[" + source + "]福利中心刷新无返回");
                return false;
            }
            JSONObject updateJson = new JSONObject(updateResponse);
            if (!goldTicketOk(TAG, updateJson)) {
                Log.error("黄金票🙈[" + source + "]福利中心刷新失败："
                        + updateJson.optString("resultDesc", updateJson.optString("memo")));
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private int logGoldTicketCollectResponse(String response, String source) {
        if (response == null || response.isEmpty()) {
            return 0;
        }
        try {
            JSONObject collectJson = new JSONObject(response);
            if (!goldTicketOk(TAG, collectJson)) {
                String message = collectJson.optString("resultDesc", collectJson.optString("memo"));
                if (message != null && !message.isEmpty()) {
                    Log.other("黄金票🙈[" + source + "]" + message);
                }
                return 0;
            }
            JSONObject result = collectJson.optJSONObject("result");
            if (result == null) {
                return 0;
            }
            JSONArray collectedList = result.optJSONArray("collectedList");
            if (collectedList == null) {
                return 0;
            }
            int count = 0;
            for (int i = 0; i < collectedList.length(); i++) {
                String item = collectedList.optString(i);
                if (item == null || item.isEmpty()) {
                    continue;
                }
                count++;
                Log.other("黄金票🙈[" + source + "]#" + item);
            }
            if (count > 0) {
                JSONObject collectedCamp = result.optJSONObject("collectedCamp");
                String totalAmount = collectedCamp != null ? collectedCamp.optString("amount") : "";
                if (totalAmount != null && !totalAmount.isEmpty()) {
                    Log.other("黄金票🙈[" + source + "]#本次共得[" + totalAmount + "]份");
                }
            }
            return count;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return 0;
        }
    }

    private void doGoldTicketCollect(JSONObject home) {
        try {
            JSONObject assetInfo = getGoldTicketAssetInfo(home);
            JSONObject toBeCollectInfo = assetInfo != null ? assetInfo.optJSONObject("toBeCollectInfo") : null;
            int totalProfitValue = toBeCollectInfo != null ? toBeCollectInfo.optInt("totalProfitValue", 0) : 0;
            if (totalProfitValue <= 0) {
                return;
            }
            int collectCount = doGoldTicketIndexCollect("场景收取");
            if (collectCount == 0) {
                Log.i("黄金票🙈[场景收取]暂无可领取奖励");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    private boolean isGoldTicketRewardReady(String status) {
        return "TO_RECEIVE".equals(status) || "WAIT_RECEIVE".equals(status)
                || "FINISHED".equals(status) || "COMPLETE".equals(status);
    }

    private boolean isGoldTicketSignupRequired(String status) {
        return "NONE_SIGNUP".equals(status) || "SIGNUP_EXPIRED".equals(status);
    }

    private boolean isGoldTicketKnownAutoTask(JSONObject task) {
        String taskId = task.optString("taskId");
        return "AP10247402".equals(taskId) || "AP11249033".equals(taskId)
                || "AP13250426".equals(taskId) || "AP19360380".equals(taskId)
                || "AP15280470".equals(taskId) || "AP16338809".equals(taskId);
    }

    private JSONArray extractGoldTicketHomeTodoTasks(JSONObject home) {
        // 首页响应在 result.upsertData.task 下，旧的 result.task 作兜底
        JSONObject task = home.optJSONObject("task");
        if (task == null) {
            JSONObject result = home.optJSONObject("result");
            if (result != null) {
                JSONObject upsertData = result.optJSONObject("upsertData");
                if (upsertData != null) {
                    task = upsertData.optJSONObject("task");
                }
                if (task == null) {
                    task = result.optJSONObject("task");
                }
            }
        }
        if (task == null) {
            return new JSONArray();
        }
        JSONObject tasks = task.optJSONObject("tasks");
        if (tasks == null) {
            return new JSONArray();
        }
        JSONArray todo = tasks.optJSONArray("todo");
        return todo != null ? todo : new JSONArray();
    }

    private JSONArray queryGoldTicketWelfareTodoTasks() {
        try {
            String welfareResponse = AntMemberRpcCall.queryWelfareHome();
            if (welfareResponse == null || welfareResponse.isEmpty()) {
                return null;
            }
            JSONObject welfareJson = new JSONObject(welfareResponse);
            if (!goldTicketOk(TAG, welfareJson)) {
                return null;
            }
            JSONObject result = welfareJson.optJSONObject("result");
            if (result == null) {
                return null;
            }
            JSONObject goldbillTasks = result.optJSONObject("goldbillTasks");
            if (goldbillTasks == null) {
                return new JSONArray();
            }
            JSONArray todo = goldbillTasks.optJSONArray("todo");
            return todo != null ? todo : new JSONArray();
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    private boolean pushGoldTicketTask(String taskId, String action) {
        try {
            String response = AntMemberRpcCall.taskQueryPush(taskId);
            if (response == null || response.isEmpty()) {
                return false;
            }
            JSONObject result = new JSONObject(response);
            if (!goldTicketOk(TAG, result)) {
                return false;
            }
            JSONObject r = result.optJSONObject("result");
            JSONObject pushResult = r != null ? r.optJSONObject("pushResult") : null;
            boolean done = pushResult != null ? pushResult.optBoolean("done", true) : true;
            return done;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private int countGoldTicketPendingAutoTasks(JSONArray todo) {
        if (todo == null || todo.length() == 0) {
            return 0;
        }
        int pending = 0;
        for (int i = 0; i < todo.length(); i++) {
            JSONObject task = todo.optJSONObject(i);
            if (task == null) {
                continue;
            }
            if (isGoldTicketKnownAutoTask(task)) {
                pending++;
            }
        }
        return pending;
    }

    /**
     * 处理黄金票任务列表（首页 / 福利中心共用）。
     * 仅放开已确认可自动闭环的已知 taskId，避免误判未知任务。
     */
    private int processGoldTicketTasks(JSONArray todo, String source) {
        if (todo == null || todo.length() == 0) {
            return 0;
        }
        int pending = 0;
        for (int i = 0; i < todo.length(); i++) {
            JSONObject task = todo.optJSONObject(i);
            if (task == null) {
                continue;
            }
            if (!isGoldTicketKnownAutoTask(task)) {
                continue;
            }
            String taskId = task.optString("taskId");
            String title = task.optString("title", taskId);
            String status = task.optString("taskProcessStatus");
            if (isGoldTicketRewardReady(status)) {
                if (pushGoldTicketTask(taskId, "receive")) {
                    Log.other("黄金票🙈[" + source + "任务领取成功]#" + title);
                } else {
                    pending++;
                }
            } else if (isGoldTicketSignupRequired(status)) {
                String triggerRes = AntMemberRpcCall.goldBillTaskTrigger(taskId);
                if (triggerRes != null && !triggerRes.isEmpty()) {
                    try {
                        if (goldTicketOk(TAG, new JSONObject(triggerRes))) {
                            Log.other("黄金票🙈[" + source + "任务报名成功]#" + title);
                            if (pushGoldTicketTask(taskId, "send")) {
                                Log.other("黄金票🙈[" + source + "任务完成]#" + title);
                            } else {
                                pending++;
                            }
                        } else {
                            pending++;
                        }
                    } catch (JSONException e) {
                        Log.printStackTrace(TAG, e);
                        pending++;
                    }
                } else {
                    pending++;
                }
            } else if ("SIGNUP_COMPLETE".equals(status)) {
                if (pushGoldTicketTask(taskId, "send")) {
                    Log.other("黄金票🙈[" + source + "任务完成]#" + title);
                } else {
                    pending++;
                }
            } else {
                if (pushGoldTicketTask(taskId, "send")) {
                    Log.other("黄金票🙈[" + source + "任务完成]#" + title);
                } else {
                    pending++;
                }
            }
        }
        return pending;
    }

    private boolean handleGoldTicketHomeTasks(JSONObject home) {
        try {
            JSONArray todo = extractGoldTicketHomeTodoTasks(home);
            processGoldTicketTasks(todo, "首页");
            JSONObject refreshed = queryGoldTicketHome();
            if (refreshed == null) {
                Log.other("黄金票🙈[首页任务复查失败]暂不写入今日完成");
                return false;
            }
            int pending = countGoldTicketPendingAutoTasks(extractGoldTicketHomeTodoTasks(refreshed));
            if (pending > 0) {
                Log.i("黄金票🙈[首页任务]#保留" + pending + "项待重试");
            }
            return pending == 0;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private boolean handleGoldTicketWelfareTasks() {
        try {
            JSONArray todo = queryGoldTicketWelfareTodoTasks();
            if (todo == null) {
                Log.error("黄金票🙈[福利中心任务查询失败]");
                return false;
            }
            processGoldTicketTasks(todo, "福利中心");
            JSONArray refreshed = queryGoldTicketWelfareTodoTasks();
            if (refreshed == null) {
                Log.other("黄金票🙈[福利中心任务复查失败]暂不写入今日完成");
                return false;
            }
            int pendingRetry = countGoldTicketPendingAutoTasks(refreshed);
            if (pendingRetry > 0) {
                Log.i("黄金票🙈[福利中心任务]#保留" + pendingRetry + "项待重试");
            }
            return pendingRetry == 0;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private void doGoldTicketConsume() {
        boolean consumeDone = false;
        try {
            Log.i("黄金票🙈[准备检查余额及提取]");
            String queryRes = AntMemberRpcCall.queryConsumeHome();
            if (queryRes == null || queryRes.isEmpty()) {
                return;
            }
            JSONObject queryJson = new JSONObject(queryRes);
            if (!goldTicketOk(TAG, queryJson)) {
                return;
            }
            JSONObject result = queryJson.optJSONObject("result");
            if (result == null) {
                return;
            }
            JSONObject assetInfo = result.optJSONObject("assetInfo");
            if (assetInfo == null) {
                return;
            }
            int availableAmount = assetInfo.optInt("availableAmount", 0);
            int minExchangeAmount = assetInfo.optInt("minExchangeAmount", 100);
            int exchangeAmountUnit = assetInfo.optInt("exchangeAmountUnit", minExchangeAmount);
            if (exchangeAmountUnit < 1) {
                exchangeAmountUnit = 1;
            }
            int extractAmount = (availableAmount / exchangeAmountUnit) * exchangeAmountUnit;
            if (extractAmount < minExchangeAmount) {
                Log.other("黄金票🙈[余额不足]#当前[" + availableAmount + "]最低需[" + minExchangeAmount + "]");
                consumeDone = true;
                return;
            }
            String productId = "";
            JSONObject product = result.optJSONObject("product");
            if (product != null) {
                productId = product.optString("productId");
            } else if (result.has("productList")) {
                JSONArray productList = result.optJSONArray("productList");
                if (productList != null && productList.length() > 0) {
                    productId = productList.optJSONObject(0).optString("productId");
                }
            } else if (assetInfo.has("mainExchangePrizeList")) {
                JSONArray list = assetInfo.optJSONArray("mainExchangePrizeList");
                if (list != null && list.length() > 0) {
                    productId = list.optJSONObject(0).optString("bizNo");
                }
            } else if (assetInfo.has("footerExchangePrizeList")) {
                JSONArray list = assetInfo.optJSONArray("footerExchangePrizeList");
                if (list != null && list.length() > 0) {
                    productId = list.optJSONObject(0).optString("bizNo");
                }
            } else {
                JSONObject backupPrize = assetInfo.optJSONObject("backupPrize");
                if (backupPrize != null && "GOLD".equalsIgnoreCase(backupPrize.optString("prizeType"))) {
                    productId = backupPrize.optString("bizNo");
                }
            }
            if (productId == null || productId.isEmpty()) {
                Log.error("黄金票🙈[提取异常]未找到有效的基金ID");
                return;
            }
            int bonusAmount = 0;
            JSONObject bonusInfo = result.optJSONObject("bonusInfo");
            if (bonusInfo != null) {
                bonusAmount = bonusInfo.optInt("bonusAmount", 0);
            }
            String exchangeMoney = null;
            JSONObject calcInfo = result.optJSONObject("calcInfo");
            if (calcInfo != null) {
                exchangeMoney = calcInfo.optString("exchangeMoney");
            }
            if (exchangeMoney == null || exchangeMoney.isEmpty()) {
                exchangeMoney = String.format("%.2f", (double) extractAmount / 1000.0);
            }
            Log.i("黄金票🙈[开始提取]#计划[" + extractAmount + "份]预计[" + exchangeMoney
                    + "元]持有[" + availableAmount + "]");
            String submitRes = AntMemberRpcCall.submitConsume(extractAmount, productId, bonusAmount);
            if (submitRes == null || submitRes.isEmpty()) {
                Log.error("黄金票🙈[提取失败]接口无返回");
                return;
            }
            JSONObject submitJson = new JSONObject(submitRes);
            if (!goldTicketOk(TAG, submitJson)) {
                String desc = submitJson.optString("resultDesc", submitJson.optString("memo"));
                if (desc != null && !desc.isEmpty()) {
                    Log.error("黄金票🙈[提取失败]" + desc);
                }
                return;
            }
            JSONObject submitResult = submitJson.optJSONObject("result");
            String writeOffNo = submitResult != null ? submitResult.optString("writeOffNo") : "";
            String successTitle = submitResult != null ? submitResult.optString("successTitle") : "";
            if ((writeOffNo != null && !writeOffNo.isEmpty())
                    || (successTitle != null && successTitle.contains("成功"))) {
                Log.other("黄金票🙈[提取成功]#" + exchangeMoney + "元#" + extractAmount + "份");
                consumeDone = true;
            } else {
                Log.error("黄金票🙈[提取失败]未返回核销码");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        } finally {
            if (consumeDone) {
                Status.flagToday("goldTicket::consume");
            }
        }
    }
    
    /**
     * 芝麻分任务处理（每日问答、公益任务、芭芭农场施肥等）
     */
    private void handleGrowthGuideTasks() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject root = new JSONObject(AntMemberRpcCall.queryGrowthBehaviorToDoList());
            if (!MessageUtil.checkResultCode(TAG, root)) {
                return;
            }
            
            // 待处理任务列表
            JSONArray toDoList = root.optJSONArray("toDoList");
            int toDoCount = toDoList == null ? 0 : toDoList.length();
            if (toDoList == null || toDoCount == 0) {
                return;
            }
            
            for (int i = 0; i < toDoList.length(); i++) {
                JSONObject task = toDoList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                
                String behaviorId = task.optString("behaviorId", "");
                String title = task.optString("title", "");
                String status = task.optString("status", "");
                String subTitle = task.optString("subTitle", "");
                
                // 公益类任务（待领取）
                if ("wait_receive".equals(status)) {
                    String openResp = AntMemberRpcCall.openBehaviorCollect(behaviorId);
                    JSONObject openJo = new JSONObject(openResp);
                    if (MessageUtil.checkResultCode(TAG, openJo)) {
                        Log.other("攒芝麻分🧾任务领取：" + title);
                    }
                    continue;
                }
                
                // 每日问答
                if ("meiriwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    if (subTitle.contains("今日已参与")) {
                        Log.other("攒芝麻分🧾[每日问答] " + subTitle + "（跳过答题）");
                        continue;
                    }
                    
                    // 查询题目
                    JSONObject quizJo = new JSONObject(AntMemberRpcCall.queryDailyQuiz(behaviorId));
                    if (!MessageUtil.checkSuccess(TAG, quizJo)) {
                        continue;
                    }
                    JSONObject data = quizJo.optJSONObject("data");
                    if (data == null) {
                        continue;
                    }
                    
                    JSONObject qVo = data.optJSONObject("questionVo");
                    if (qVo == null) {
                        continue;
                    }
                    
                    JSONObject rightAnswer = qVo.optJSONObject("rightAnswer");
                    if (rightAnswer == null) {
                        continue;
                    }
                    
                    long bizDate = data.optLong("bizDate", 0L);
                    String questionId = qVo.optString("questionId", "");
                    String questionContent = qVo.optString("questionContent", "");
                    String answerId = rightAnswer.optString("answerId", "");
                    String answerContent = rightAnswer.optString("answerContent", "");
                    
                    if (bizDate <= 0 || questionId.isEmpty() || answerId.isEmpty()) {
                        continue;
                    }
                    
                    // 提交答案
                    JSONObject pushJo = new JSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, "RIGHT"));
                    if (MessageUtil.checkResultCode(TAG, pushJo)) {
                        Log.other("攒芝麻分🎖️[每日答题成功] " + StringUtil.truncate(questionContent, 200)
                                + " | 答案=" + StringUtil.truncate(answerContent, 200) + "(" + answerId + ")"
                                + (subTitle.isEmpty() ? "" : " | " + subTitle));
                    }
                }
                
                // 视频问答
                if ("shipingwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    long bizDate = System.currentTimeMillis();
                    String questionId = "question3";
                    String answerId = "A";
                    String answerType = "RIGHT";
                    
                    jo = new JSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, answerType));
                    
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("攒芝麻分🎖️[视频问答提交成功]");
                    }
                }
                
                // 芭芭农场施肥
                if ("babanongchang_7d".equals(behaviorId) && "wait_doing".equals(status)) {
                    
                    // 获取WUA
                    String wua = new AntOrchard().getWua();
                    String source = "DNHZ_NC_zhimajingnangSF";
                    
                    JSONObject spreadManureData = new JSONObject(AntOrchardRpcCall.orchardSpreadManure("main", false, wua));
                    
                    if (!"100".equals(spreadManureData.optString("resultCode"))) {
                        continue;
                    }
                    
                    String taobaoDataStr = spreadManureData.optString("taobaoData", "");
                    if (taobaoDataStr.isEmpty()) {
                        continue;
                    }
                    
                    JSONObject spreadTaobaoData = new JSONObject(taobaoDataStr);
                    
                    JSONObject currentStage = spreadTaobaoData.optJSONObject("currentStage");
                    if (currentStage == null) {
                        Log.error(TAG + "GrowthGuideTasks" + "芭芭农场[缺少currentStage]");
                        continue;
                    }
                    
                    String stageText = currentStage.optString("stageText", "");
                    JSONObject statistics = spreadTaobaoData.optJSONObject("statistics");
                    int dailyAppWateringCount = statistics == null ? 0 : statistics.optInt("dailyAppWateringCount", 0);
                    
                    Log.farm("芭芭农场🌳施肥" + dailyAppWateringCount + "次[" + stageText + "]");
                    Log.other("攒芝麻分🎖️芭芭农场施肥[" + title + "]已施肥" + dailyAppWateringCount + "次");
                    
                }
            }
        }
        catch (Throwable e) {
            Log.printStackTrace(TAG + ".handleGrowthGuideTasks", e);
        }
    }

    
    public static void queryAndCollect() {
        try {
            // 1. 查询进度球状态
            String queryResp = AntMemberRpcCall.queryScoreProgress();
            if (queryResp == null || queryResp.isEmpty()) {
                return;
            }
            
            JSONObject json = new JSONObject(queryResp);
            
            // 检查 success
            if (!MessageUtil.checkSuccess(TAG, json)) {
                return;
            }
            
            JSONObject totalWait = json.optJSONObject("totalWaitProcessVO");
            if (totalWait == null) {
                return;
            }
            
            JSONArray idList = totalWait.optJSONArray("totalProgressIdList");
            if (idList == null || idList.length() == 0) {
                return;
            }
            
            // 直接传 JSONArray
            String collectResp = AntMemberRpcCall.collectProgressBall(idList);
            if (collectResp == null) {
                return;
            }
            
            JSONObject collectJson = new JSONObject(collectResp);
            int collectedAccelerateProgress = collectJson.optInt("collectedAccelerateProgress", -1);
            int currentAccelerateValue = collectJson.optInt("currentAccelerateValue", 0);
            int totalAccelerateProgress = collectJson.optInt("totalAccelerateProgress", 0);
            Log.other("攒芝麻分🎁领取#本次加速进度:" + collectedAccelerateProgress + "(总" + totalAccelerateProgress + "%)加速倍率:" + currentAccelerateValue);
        }
        catch (JSONException e) {
            Log.printStackTrace(TAG + "queryAndCollect JSON err", e);
        }
        catch (Exception e) {
            Log.printStackTrace(TAG + "queryAndCollect err", e);
        }
    }
    
    /**
     * 收取黄金票
     */
    private void goldBillCollect(String signInfo) {
        try {
            String str = AntMemberRpcCall.goldBillCollect(signInfo);
            JSONObject jsonObject = new JSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".goldBillCollect.goldBillCollect", jsonObject.optString("resultDesc"));
                return;
            }
            JSONObject object = jsonObject.getJSONObject("result");
            JSONArray jsonArray = object.getJSONArray("collectedList");
            int length = jsonArray.length();
            if (length == 0) {
                return;
            }
            for (int i = 0; i < length; i++) {
                Log.other("黄金票🙈[" + jsonArray.getString(i) + "]");
            }
            Log.other("黄金票🏦本次总共获得[" + JsonUtil.getValueByPath(object, "collectedCamp.amount") + "]");
        }
        catch (Throwable th) {
            Log.err(TAG, "signIn err:", th);
        }
    }
    //游戏中心任务
    
    /**
     * 批量领取玩乐豆
     */
    public static void batchReceivePointBall() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.batchReceivePointBall());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.getJSONObject("data");
                String totalAmount = dataObj.getString("totalAmount");
                Log.other("游戏中心🎮批量领取#获得[" + totalAmount + "玩乐豆]");
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "batchReceivePointBall err:", t);
        }
    }
    
    /**
     * 每日签到
     *
     * @return 签到是否成功
     */
    public static boolean dailySignIn() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.continueSignIn());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject toastModule = jsonObject.getJSONObject("data").getJSONObject("autoSignInToastModule");
                String desc = toastModule.getString("desc");
                String beanNum = desc.substring(desc.indexOf("玩乐豆+") + 4);
                Log.other("游戏中心🎮每日签到#获得[" + beanNum + "玩乐豆]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "continueSignIn err:", t);
        }
        return false;
    }
    
    /**
     * 处理单个任务
     *
     * @param taskObj 任务JSON对象
     */
    public void processTask(JSONObject taskObj) {
        try {
            String actionType = taskObj.optString("actionType");
            String taskId = taskObj.getString("taskId");
            String subTitle = taskObj.getString("subTitle");
            String taskStatus = taskObj.getString("taskStatus");
            int prizeAmount = taskObj.optInt("prizeAmount", 0);

            //黑名单任务跳过
            if (AntMemberTaskList.getValue().contains(subTitle)) {
                return;
            }
            // 任务未完成且需要报名（needSignUp 可能缺字段，用 optBoolean 避免整条任务被异常打断）
            if ("NOT_DONE".equals(taskStatus) && taskObj.optBoolean("needSignUp", false)) {
                JSONObject jsonObject = new JSONObject(AntMemberRpcCall.doTaskSignup(taskId));
                if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", subTitle, jsonObject);
                    return;
                }
            }

            // 执行任务：原先只处理 actionType=VIEW，其它类型直接 return（列表拿到了却静默不处理、
            // 连日志都没有）。现在各类都尝试一次。
            JSONObject doTaskjo = new JSONObject(AntMemberRpcCall.doTaskSend(taskId));
            if (MessageUtil.checkSuccess(TAG, doTaskjo)) {
                Log.other("游戏中心🎮完成任务[" + subTitle + "]#待领[" + prizeAmount + "玩乐豆]");
            } else {
                // doTaskSend 常被 400000040 拒绝，改用另一种实现方案（见 TaskAlternative）
                String sceneCode = taskObj.optString("sceneCode", "").trim();
                if (TaskAlternative.hit(doTaskjo, sceneCode)) {
                    // 另一种实现方案（见 TaskAlternative）；version 传本模块原值
                    TaskAlternative.trigger(pendingVerifyTasks, taskId, subTitle, taskId, sceneCode,
                            AntMemberRpcCall.DO_FARM_TASK_VERSION, "游戏中心", msg -> Log.other(msg));
                } else {
                    Log.other("游戏中心⚠️未完成[" + subTitle + "]");
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", subTitle, doTaskjo);
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "doTask err:", t);
        }
    }

    /**
     * 核对「已触发但响应不可信」的游戏中心任务：等几秒后重拉任务列表，**仍为 NOT_DONE** 的才计入自动拉黑。
     * <p>为什么以列表为准：{@code doFarmTask} 会回 102「服务器正在开小差」等错码但任务其实已生效，
     * 服务端是异步推进状态的，只有列表里的 {@code taskStatus} 才是最终判据。
     */
    private void verifyPendingTasks() {
        TaskAlternative.verify(pendingVerifyTasks, VERIFY_CFG, () -> {
            Set<String> notDone = new LinkedHashSet<>();
            collectNotDoneIds(AntMemberRpcCall.queryModularTaskList(), notDone);
            return notDone;
        });
    }

    /**
     * 从任务列表响应里收集 {@code taskStatus=NOT_DONE} 的 taskId。
     * <p>两套列表结构不同（v3 {@code data.taskModuleList[].taskList[]}、
     * v4 {@code data.gameTaskModule.gameTaskList[]}），这里都解析一遍，避免漏判导致误拉黑。
     */
    private static void collectNotDoneIds(String response, Set<String> out) {
        try {
            JSONObject data = new JSONObject(response).optJSONObject("data");
            if (data == null) {
                return;
            }
            JSONArray modules = data.optJSONArray("taskModuleList");
            if (modules != null) {
                for (int i = 0; i < modules.length(); i++) {
                    JSONObject module = modules.optJSONObject(i);
                    collectNotDoneFromArray(module == null ? null : module.optJSONArray("taskList"), out);
                }
            }
            JSONObject gameTaskModule = data.optJSONObject("gameTaskModule");
            if (gameTaskModule != null) {
                collectNotDoneFromArray(gameTaskModule.optJSONArray("gameTaskList"), out);
            }
        } catch (Throwable t) {
            Log.err(TAG, "collectNotDoneIds err:", t);
        }
    }

    private static void collectNotDoneFromArray(JSONArray tasks, Set<String> out) {
        if (tasks == null) {
            return;
        }
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || !"NOT_DONE".equals(task.optString("taskStatus", "").trim())) {
                continue;
            }
            String taskId = task.optString("taskId", "").trim();
            if (!taskId.isEmpty()) {
                out.add(taskId);
            }
        }
    }
    
    /**
     * 查询并处理任务列表
     */
    public void queryAndProcessTaskList() {
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.queryModularTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            if (!jsonObject.has("data")) {
                return;
            }
            JSONArray taskModuleList = jsonObject.getJSONObject("data").getJSONArray("taskModuleList");
            for (int i = 0; i < taskModuleList.length(); i++) {
                JSONObject moduleObj = taskModuleList.getJSONObject(i);
                JSONArray taskList = moduleObj.getJSONArray("taskList");
                for (int j = 0; j < taskList.length(); j++) {
                    processTask(taskList.getJSONObject(j));
                }
            }
            // 核对本轮 doFarmTask 的结果（响应不可信，以任务列表为准）
            verifyPendingTasks();
        }
        catch (Throwable t) {
            Log.err(TAG, "queryModularTaskList err:", t);
        }
    }
    
    /**
     * 游戏中心（会员场景 xlyy_WJCNJT）宝箱领取。
     * <p>任务流 {@code queryGameFeeds} 里的条目都是「玩游戏得1个宝箱」（只有
     * gameId/cardId/appId + taskDesc，没有 taskId/taskStatus）；官方客户端在该场景同样只查不写
     * （query* + batchReceiveTaskPrize），宝箱由游戏侧上报才 +1——实测 doFarmTask 的
     * bizKey/taskSceneCode 七种组合全回 307「任务不存在」，所以这里不对任务流发完成请求，
     * 只在首页报有待领时把宝箱领掉；任务名仍进「会员任务」黑名单候选，供手动剔除。
     * <p>待领判据取官方字段：{@code taskModule.needReceive} / {@code needReceiveTaskCertCnt}，
     * 上限 {@code reachTaskCertLimit}（{@code dailyTaskCertCntUpperLimit}，30/天）。
     */
    public void gameCenterTaskPrize() {
        try {
            JSONObject homeJo = new JSONObject(AntMemberRpcCall.gameCenterHomePage());
            if (!MessageUtil.checkSuccess(TAG, homeJo)) {
                Log.record("游戏中心首页异常：" + homeJo);
                return;
            }
            JSONObject homeData = homeJo.optJSONObject("data");
            JSONObject taskModule = homeData == null ? null : homeData.optJSONObject("taskModule");
            if (taskModule == null) {
                return;
            }
            if (taskModule.optBoolean("reachTaskCertLimit", false)) {
                Log.record("游戏中心宝箱已达今日上限（"
                        + taskModule.optString("taskProgressDesc", GAME_CENTER_CERT_LIMIT + "/天") + "）");
                return;
            }
            boolean needReceive = homeData.has("taskPrizePopup") || taskModule.optBoolean("needReceive", false)
                    || taskModule.optInt("needReceiveTaskCertCnt", 0) > 0;
            if (!needReceive) {
                return;
            }

            JSONObject receiveJo = new JSONObject(AntMemberRpcCall.batchReceiveTaskPrize());
            if (!MessageUtil.checkSuccess(TAG, receiveJo)) {
                Log.record("游戏中心宝箱领取失败：" + receiveJo);
                return;
            }
            JSONObject receiveData = receiveJo.optJSONObject("data");
            JSONObject prizePopup = receiveData == null ? null : receiveData.optJSONObject("prizePopup");
            String tip = prizePopup == null ? "" : prizePopup.optString("subTitle", "").trim();
            if (tip.isEmpty() && prizePopup != null) {
                tip = prizePopup.optString("actionText", "").trim();
            }
            String desc = taskModule.optString("taskProgressDesc", "").trim();
            Log.other("游戏中心🎁领取宝箱" + (tip.isEmpty() ? "" : "#" + tip) + (desc.isEmpty() ? "" : "#" + desc));
        }
        catch (Throwable t) {
            Log.err(TAG, "gameCenterTaskPrize err:", t);
        }
    }

    /**
     * 游戏中心任务流条目在「会员任务｜黑名单列表」里的标题（如「九梦仙域：玩游戏得1个宝箱」）。
     * <p>字段实测（2026-10-07 抓包）：{@code mainTitle}=游戏名、{@code subTitle}=宣传语、
     * {@code taskDesc}=「玩游戏得1个宝箱」、{@code taskTagText}=「玩游戏得」。
     */
    private static String gameCenterTaskTitle(JSONObject feed) {
        String title = firstNonEmpty(feed, "mainTitle", "subTitle");
        String taskDesc = firstNonEmpty(feed, "taskDesc", "taskTagText");
        if (title.isEmpty()) {
            title = taskDesc;
        } else if (!taskDesc.isEmpty() && !title.contains(taskDesc)) {
            title = title + "：" + taskDesc;
        }
        if (title.isEmpty()) {
            title = firstNonEmpty(feed, "gameId", "cardId", "appId");
        }
        return title;
    }

    /** 按顺序取第一个非空字符串字段。 */
    private static String firstNonEmpty(JSONObject jo, String... keys) {
        for (String key : keys) {
            String value = jo.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }
    
    /**
     * 查询玩乐豆小球列表，有则领取
     */
    public static void queryPointBallList() {
        try {
            String response = ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", "[{}]");
            JSONObject jsonObject = new JSONObject(response);
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONArray pointBallList = jsonObject.getJSONObject("data").getJSONArray("pointBallList");
                if (pointBallList.length() > 0) {
                    batchReceivePointBall();
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryPointBallList err:", t);
        }
    }

    /**
     * 检查并执行签到
     */
    public static void checkAndDoSignIn() {
        if (Status.hasFlagToday("gameCenterSignIn")) {
            return;
        }
        
        try {
            JSONObject jsonObject = new JSONObject(AntMemberRpcCall.queryPointBallList());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.getJSONObject("data");
                if (dataObj.has("signInBallModule")) {
                    JSONObject signInModule = dataObj.getJSONObject("signInBallModule");
                    if (!signInModule.getBoolean("signInStatus")) {
                        if (dailySignIn()) {
                            Status.flagToday("gameCenterSignIn");
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "querySignInBall err:", t);
        }
    }
    
    /*
    private void enableGameCenter() {
        try {
            try {
                String str = AntMemberRpcCall.querySignInBall();
                JSONObject jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.querySignInBall", jsonObject.optString("resultDesc"));
                    return;
                }
                str = JsonUtil.getValueByPath(jsonObject, "data.signInBallModule.signInStatus");
                if (String.valueOf(true).equals(str)) {
                    return;
                }
                str = AntMemberRpcCall.continueSignIn();
                TimeUtil.sleep(300);
                jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.continueSignIn", jsonObject.optString("resultDesc"));
                    return;
                }
                Log.record("游戏中心🎮签到成功");
            }
            catch (Throwable th) {
                Log.err(TAG, "signIn err:", th);
            }
            try {
                String str = AntMemberRpcCall.queryPointBallList();
                JSONObject jsonObject = new JSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".batchReceive.queryPointBallList", jsonObject.optString("resultDesc"));
                    return;
                }
                JSONArray jsonArray = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "data.pointBallList");
                if (jsonArray == null || jsonArray.length() == 0) {
                    return;
                }
                str = AntMemberRpcCall.batchReceivePointBall();
                TimeUtil.sleep(300);
                jsonObject = new JSONObject(str);
                if (jsonObject.optBoolean("success")) {
                    Log.other("游戏中心🎮全部领取成功[" + JsonUtil.getValueByPath(jsonObject, "data.totalAmount") + "]乐豆");
                }
                else {
                    Log.i(TAG + ".batchReceive.batchReceivePointBall", jsonObject.optString("resultDesc"));
                }
            }
            catch (Throwable th) {
                Log.err(TAG, "batchReceive err:", th);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    */
    private void memberPointExchangeBenefit() {
        try {
            AntMemberExchange.fetchDynamicBenefits();
            if (!memberPointExchangeBenefit.getValue()) {
                Log.i(TAG, "会员积分兑换开关已关闭，仅更新权益库");
                return;
            }
            java.util.Set<String> selectedIds = memberPointExchangeBenefitList.getValue();
            AntMemberExchange.exchangeSelected(selectedIds, 300);
            AntMemberExchange.exchangeCustom(memberPointExchangeCustom.getValue(), 300);
        }
        catch (Throwable t) {
            Log.err(TAG, "memberPointExchangeBenefit err:", t);
        }
    }
    
    private void collectSesame() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject entrance = jo.getJSONObject("entrance");
            if (!entrance.optBoolean("openApp")) {
                Log.other("芝麻信用💌未开通");
                return;
            }
            
            jo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("data")) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.has("toCompleteVOS")) {
                return;
            }
            JSONArray toCompleteVOS = data.getJSONArray("toCompleteVOS");
            // 本轮已上报的任务（templateId → 标题）及上报前完成度，用于回读校验
            LinkedHashMap<String, String> reported = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> beforeComplete = new LinkedHashMap<>();
            for (int i = 0; i < toCompleteVOS.length(); i++) {
                JSONObject toCompleteVO = toCompleteVOS.getJSONObject(i);
                String taskTitle = toCompleteVO.has("title") ? toCompleteVO.getString("title") : "未知任务";
                //黑名单任务跳过
                if (MemberCreditSesameTaskList.getValue().contains(taskTitle)) {
                    continue;
                }
                
                boolean finishFlag = toCompleteVO.optBoolean("finishFlag", false);
                String actionText = toCompleteVO.optString("actionText", "");
                
                // 检查任务是否已完成
                if (finishFlag || "已完成".equals(actionText)) {
                    continue;
                }
                
                if (!toCompleteVO.has("templateId")) {
                    continue;
                }
                
                String taskTemplateId = toCompleteVO.getString("templateId");
                int needCompleteNum = toCompleteVO.has("needCompleteNum") ? toCompleteVO.getInt("needCompleteNum") : 1;
                int completedNum = toCompleteVO.optInt("completedNum", 0);
                // 今日已上报且回读确认状态没推进：不再重复上报（次日再试），避免每轮刷同一批
                if (Status.hasFlagToday("AntMember::sesame::" + taskTitle)) {
                    continue;
                }
                
                // 上报被受理 ≠ 任务完成：无论走到哪一步都登记回读，真实状态一律以回读为准
                String recordId = activeSesameRecordId(data, taskTemplateId);
                if (recordId == null || !reportSesameTask(taskTitle, taskTemplateId, recordId)) {
                    continue;
                }
                reported.put(taskTemplateId, taskTitle);
                beforeComplete.put(taskTemplateId, completedNum);
                
                jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray ja = jo.getJSONArray("creditFeedbackVOS");
                for (int j = 0; j < ja.length(); j++) {
                    jo = ja.getJSONObject(j);
                    if (!"UNCLAIMED".equals(jo.getString("status"))) {
                        continue;
                    }
                    //String title = jo.getString("title");
                    String creditFeedbackId = jo.getString("creditFeedbackId");
                    String potentialSize = jo.getString("potentialSize");
                    jo = new JSONObject(AntMemberRpcCall.collectCreditFeedback(creditFeedbackId));
                    TimeUtil.sleep(300);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("收芝麻粒🙇🏻‍♂️领取[" + taskTitle + "]奖励[芝麻粒*" + potentialSize + "]");
                    }
                }
            }
            
            // 回读校验：上报被受理不等于任务完成，按服务端最新完成度判定，日志只写真实结果
            if (!reported.isEmpty()) {
                TimeUtil.sleep(500);
                JSONObject freshJo = new JSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
                LinkedHashMap<String, String> freshProgress = new LinkedHashMap<>();
                boolean freshOk = false;
                if (MessageUtil.checkResultCode(TAG, freshJo) && freshJo.has("data")) {
                    JSONArray freshList = freshJo.getJSONObject("data").optJSONArray("toCompleteVOS");
                    freshOk = true;
                    if (freshList != null) {
                        for (int k = 0; k < freshList.length(); k++) {
                            JSONObject vo = freshList.getJSONObject(k);
                            String tid = vo.optString("templateId", "");
                            if (tid.isEmpty()) {
                                continue;
                            }
                            int need = vo.has("needCompleteNum") ? vo.getInt("needCompleteNum") : 1;
                            if (vo.optBoolean("finishFlag", false) || "已完成".equals(vo.optString("actionText", ""))) {
                                freshProgress.put(tid, "DONE");
                            } else {
                                freshProgress.put(tid, vo.optInt("completedNum", 0) + "/" + need);
                            }
                        }
                    }
                }
                for (Map.Entry<String, String> entry : reported.entrySet()) {
                    String tid = entry.getKey();
                    String title = entry.getValue();
                    Integer before = beforeComplete.get(tid);
                    int beforeNum = before == null ? 0 : before;
                    if (!freshOk) {
                        Log.other("芝麻信用💳[" + title + "]已上报，回读校验未执行");
                        continue;
                    }
                    String progress = freshProgress.get(tid);
                    if (progress == null) {
                        Log.other("芝麻信用💳完成任务[" + title + "]#已不在待完成列表");
                        continue;
                    }
                    if ("DONE".equals(progress)) {
                        Log.other("芝麻信用💳完成任务[" + title + "]#已标记完成");
                        continue;
                    }
                    int slash = progress.indexOf('/');
                    int nowNum = slash <= 0 ? 0 : Integer.parseInt(progress.substring(0, slash));
                    int needNum = slash <= 0 ? 1 : Integer.parseInt(progress.substring(slash + 1));
                    if (nowNum <= beforeNum) {
                        Log.other("芝麻信用💳[" + title + "]上报未生效#仍为(" + progress + "天)，今日不再重试");
                        Status.flagToday("AntMember::sesame::" + title);
                        // 这类任务服务端要求真实参与（push 的 promiseActivityExtCheck 校验），自动完成不了：
                        // 走"连续命中确认"（累计 3 次才真拉黑），避免把服务端异步未落状态的正常任务一次误杀
                        if (AutoMemberCreditSesameTaskList.getValue()) {
                            MessageUtil.MarkTaskBlackListConfirm("AntMember", "MemberCreditSesameTaskList", "芝麻粒任务", title);
                        }
                    } else if (nowNum >= needNum) {
                        Log.other("芝麻信用💳完成任务[" + title + "]#(" + progress + "天)");
                    } else {
                        // 多天任务：今天只推进一份，剩下的留给次日
                        Log.other("芝麻信用💳完成任务[" + title + "]#(" + progress + "天)，剩余次日继续");
                        Status.flagToday("AntMember::sesame::" + title);
                    }
                }
            }
            jo = new JSONObject(AntMemberRpcCall.queryCreditFeedback());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray creditFeedbackVOS = jo.getJSONArray("creditFeedbackVOS");
            if (creditFeedbackVOS.length() != 0) {
                jo = new JSONObject(AntMemberRpcCall.collectAllCreditFeedback());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    String resultCode = jo.optString("resultCode");
                    Log.other("收芝麻粒🙇🏻‍♂️[一键收取]" + resultCode);
                }
            }
            
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 芝麻粒任务上报：joinActivity → taskFeedback → pushActivity 须依次全部发送；
     * join 因"存在进行中的记录"被拒时，刷新列表取本任务那条记录继续推完。
     * <p>返回是否走到 push；未发送 push 的不计失败，也不登记回读。
     *
     * @param recordId 任务列表已给出的进行中记录，为空时才发 join
     */
    private boolean reportSesameTask(String taskTitle, String taskTemplateId, String recordId) {
        try {
            if (StringUtil.isEmpty(recordId)) {
                JSONObject joinJo = new JSONObject(AntMemberRpcCall.joinSesameTask(taskTemplateId));
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, joinJo)) {
                    JSONObject joinData = joinJo.optJSONObject("data");
                    recordId = joinData == null ? "" : joinData.optString("recordId", "");
                } else {
                    // 全局只允许一条进行中记录：刷新列表取本任务那条，取不到再回落到最近操作记录
                    if ("PROMISE_HAS_PROCESSING_TEMPLATE".equals(joinJo.optString("resultCode"))) {
                        JSONObject fresh = new JSONObject(AntMemberRpcCall.queryAvailableSesameTask());
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, fresh) && fresh.optJSONObject("data") != null) {
                            recordId = activeSesameRecordId(fresh.optJSONObject("data"), taskTemplateId);
                            if (recordId == null) {
                                return false;
                            }
                        }
                    }
                    if (StringUtil.isEmpty(recordId)) {
                        recordId = lastOperateRecordId(taskTemplateId);
                    }
                    if (!StringUtil.isEmpty(recordId)) {
                        Log.other("芝麻信用💳[" + taskTitle + "]沿用进行中的记录");
                    }
                }
            }
            if (StringUtil.isEmpty(recordId)) {
                Log.other("芝麻信用💳上报[" + taskTitle + "]无可用记录#未发送反馈与push，不计失败");
                return false;
            }
            
            JSONObject feedbackJo = new JSONObject(AntMemberRpcCall.feedBackSesameTaskNew(taskTemplateId));
            TimeUtil.sleep(500);
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, feedbackJo);
            if (!MessageUtil.checkResultCode(TAG, feedbackJo)) {
                Log.other("芝麻信用💳上报[" + taskTitle + "]未受理#未发送push");
                return false;
            }
            
            JSONObject pushJo = new JSONObject(AntMemberRpcCall.finishSesameTask(recordId, ""));
            TimeUtil.sleep(500);
            if ("ILLEGAL_ARGUMENT".equals(pushJo.optString("resultCode"))) {
                // 真实跳转类任务不可能自动完成，服务端固定拒 push：一次即永久拉黑，该错误不再打印
                if (AutoMemberCreditSesameTaskList.getValue()) {
                    MessageUtil.MarkTaskBlackListPermanent("AntMember", "MemberCreditSesameTaskList", "芝麻粒任务", taskTitle);
                    Log.other("芝麻信用💳[" + taskTitle + "]#真实跳转类，无法自动完成，已加入永久黑名单");
                } else {
                    Log.other("芝麻信用💳[" + taskTitle + "]#真实跳转类，无法自动完成");
                }
                return false;
            }
            if (!MessageUtil.checkResultCode(TAG, pushJo)) {
                Log.other("芝麻信用💳[" + taskTitle + "]push被拒#不影响完成判定");
            }
            return true;
            
        } catch (Throwable t) {
            Log.err(TAG, "reportSesameTask err:", t);
        }
        return false;
    }
    
    /**
     * 取回「最近一次操作任务」的 recordId：仅当它就是本任务且仍在进行中（finishFlag=false）时返回，否则 null。
     * <p>用途见 {@link #reportSesameTask}：join 被「存在进行中的生活记录」拒绝时，必须用原记录的 recordId 才能推完它。
     */
    private String lastOperateRecordId(String taskTemplateId) {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryLastOperateTask());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            JSONObject data = jo.optJSONObject("data");
            JSONObject vo = data == null ? null : data.optJSONObject("lastOperateTaskVO");
            if (vo == null || !taskTemplateId.equals(vo.optString("templateId", ""))) {
                return null;
            }
            if (vo.optBoolean("finishFlag", false)) {
                return null;
            }
            String recordId = vo.optString("recordId", "");
            return StringUtil.isEmpty(recordId) ? null : recordId;
        } catch (Throwable t) {
            Log.err(TAG, "lastOperateRecordId err:", t);
        }
        return null;
    }
    
    /**
     * 从任务列表取本任务进行中的记录：recordId 须为字符串且仍在进行中，同一任务出现多条不一致记录时返回 null。
     *
     * @return 进行中的 recordId；空串表示列表无本任务记录，null 表示列表数据可疑
     */
    private String activeSesameRecordId(JSONObject data, String taskTemplateId) {
        JSONObject daily = data.optJSONObject("dailyTaskListVO");
        JSONArray[] lists = {data.optJSONArray("toCompleteVOS"),
                daily == null ? null : daily.optJSONArray("waitCompleteTaskVOS"),
                daily == null ? null : daily.optJSONArray("waitJoinTaskVOS")};
        String recordId = "";
        for (JSONArray list : lists) {
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject task = list.optJSONObject(i);
                if (task == null || !taskTemplateId.equals(task.optString("templateId", ""))) {
                    continue;
                }
                Object value = task.opt("recordId");
                if (value == null || JSONObject.NULL.equals(value) || "".equals(value)) {
                    continue;
                }
                boolean valid = value instanceof String && !((String) value).trim().isEmpty()
                        && Boolean.FALSE.equals(task.opt("finishFlag"))
                        && task.optInt("completedNum", 0) < task.optInt("needCompleteNum", 1)
                        && (recordId.isEmpty() || recordId.equals(value));
                if (!valid) {
                    Log.other("芝麻信用💳记录校验未通过#[" + taskTemplateId + "]跳过");
                    return null;
                }
                recordId = (String) value;
            }
        }
        return recordId;
    }
    
    private void CheckInTaskRpcManager() {
        if (Status.hasFlagToday("AntMember::zmlCheckIn")) {
            return;
        }
        // 领取是否失败：失败时不置今日标记，留给下一轮重试（否则当天不再重试 → 漏领）
        boolean claimFailed = false;
        try {
            
            String checkInRes = AntMemberRpcCall.alchemyQueryCheckIn("zml");
            JSONObject checkInJo = new JSONObject(checkInRes);
            if (MessageUtil.checkResultCode(TAG, checkInJo)) {
                JSONObject data = checkInJo.optJSONObject("data");
                if (data != null) {
                    JSONObject currentDay = data.optJSONObject("currentDateCheckInTaskVO");
                    if (currentDay != null) {
                        String status = currentDay.optString("status");
                        String checkInDate = currentDay.optString("checkInDate");
                        if ("CAN_COMPLETE".equals(status) && !checkInDate.isEmpty()) {
                            String completeRes = AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "zml");
                            try {
                                JSONObject completeJo = new JSONObject(completeRes);
                                if (MessageUtil.checkResultCode(TAG, completeJo)) {
                                    JSONObject prize = completeJo.optJSONObject("data");
                                    int num = 0;
                                    if (prize != null) {
                                        num = prize.optInt("zmlNum", prize.optJSONObject("prize") != null ? prize.optJSONObject("prize").optInt("num", 0) : 0);
                                    }
                                    Log.other("收芝麻粒🙇🏻‍♂️领取[每日签到成功]#获得" + num + "粒");
                                }
                                else {
                                    claimFailed = true;
                                    Log.error(".doSesameAlchemy#" + "签到失败:" + completeRes);
                                }
                            }
                            catch (Throwable e) {
                                claimFailed = true;
                                Log.printStackTrace(TAG + ".doSesameAlchemy.alchemyCheckInComplete", e);
                            }
                        }
                    }
                }
            }
            if (claimFailed) {
                Log.other("收芝麻粒🙇🏻‍♂️签到领取失败#本轮不置今日标记，稍后重试");
            }
            else {
                Status.flagToday("AntMember::zmlCheckIn");
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG + ".doSesameZmlCheckIn", t);
        }
    }
    
    private void doSesameAlchemy() {
        try {
            JSONObject homeJo = new JSONObject(AntMemberRpcCall.alchemyQueryHome());
            if (!MessageUtil.checkResultCode(TAG, homeJo)) {
                Log.other("芝麻炼金⚗️首页查询失败");
                return;
            }
            JSONObject homeData = homeJo.optJSONObject("data");
            if (homeData == null) {
                return;
            }
            int zmlBalance = homeData.optInt("zmlBalance", 0);
            int alchemyCostZml = homeData.optInt("alchemyCostZml", 5);
            boolean capReached = homeData.optBoolean("capReached", false);
            int currentLevel = homeData.optInt("currentLevel", 0);
            
            alchemyCheckIn();
            alchemyTimeLimitedTask();
            
            int level = currentLevel;
            boolean full = capReached;
            int balance = zmlBalance;
            while (balance >= alchemyCostZml && !full) {
                JSONObject executeJo = new JSONObject(AntMemberRpcCall.doAlchemy());
                if (!MessageUtil.checkResultCode(TAG, executeJo)) {
                    Log.other("芝麻炼金⚗️[炼金失败]");
                    break;
                }
                JSONObject data = executeJo.optJSONObject("data");
                if (data == null) {
                    break;
                }
                boolean levelUp = data.optBoolean("levelUp", false);
                boolean levelFull = data.optBoolean("levelFull", false);
                int goldNum = data.optInt("goldNum", 0);
                if (levelUp) {
                    level++;
                }
                if (levelFull) {
                    full = true;
                }
                Log.other("芝麻炼金⚗️[炼金成功]#消耗" + alchemyCostZml + "粒 | 获得" + goldNum + "金 | 当前等级Lv." + level + (levelUp ? "（升级🎉）" : "") + (levelFull ? "（满级🏆）" : ""));
                balance -= alchemyCostZml;
                alchemyCheckIn();
                alchemyTimeLimitedTask();
                TimeUtil.sleep(300);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG + ".doSesameAlchemy", t);
        }
    }
    
    private void alchemyCheckIn() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.alchemyQueryCheckIn("zml"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null) {
                return;
            }
            JSONObject currentDay = data.optJSONObject("currentDateCheckInTaskVO");
            if (currentDay == null) {
                return;
            }
            String status = currentDay.optString("status");
            String checkInDate = currentDay.optString("checkInDate");
            if ("CAN_COMPLETE".equals(status) && !checkInDate.isEmpty()) {
                JSONObject completeJo = new JSONObject(AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "alchemy"));
                if (MessageUtil.checkResultCode(TAG, completeJo)) {
                    JSONObject prizeData = completeJo.optJSONObject("data");
                    int num = 0;
                    if (prizeData != null) {
                        JSONObject prize = prizeData.optJSONObject("prize");
                        num = prizeData.optInt("zmlNum", prize != null ? prize.optInt("num", 0) : 0);
                    }
                    Log.other("芝麻炼金⚗️[每日签到成功]#获得" + num + "粒");
                }
                else {
                    Log.other("芝麻炼金⚗️[炼金签到失败]");
                }
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG + ".doSesameAlchemy.alchemyCheckInComplete", t);
        }
    }
    
    private void alchemyTimeLimitedTask() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.alchemyQueryTimeLimitedTask());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null) {
                return;
            }
            JSONObject taskVO = data.optJSONObject("timeLimitedTaskVO");
            if (taskVO == null) {
                Log.other("芝麻炼金⚗️[当前没有时段奖励任务]");
                return;
            }
            String longTitle = taskVO.optString("longTitle", "未知任务");
            String templateId = taskVO.optString("templateId");
            if (templateId.isEmpty()) {
                Log.other("芝麻炼金⚗️[任务跳过] templateId 为空");
                return;
            }
            int state = taskVO.optInt("state", 0);
            boolean tomorrow = taskVO.optBoolean("tomorrow", false);
            int rewardAmount = taskVO.optInt("rewardAmount", 0);
            Log.other("芝麻炼金⚗️[任务检查] 任务=" + longTitle + " 状态=" + state + " 奖励=" + rewardAmount + " 明天=" + tomorrow);
            if (tomorrow) {
                Log.other("芝麻炼金⚗️[任务跳过] 任务=" + longTitle + " 是明天的奖励");
                return;
            }
            if (state != 1) {
                Log.other("芝麻炼金⚗️[当前不可领取] 任务=" + longTitle);
                return;
            }
            Log.other("芝麻炼金⚗️[开始领取任务奖励] 任务=" + longTitle);
            JSONObject completeJo = new JSONObject(AntMemberRpcCall.alchemyCompleteTimeLimitedTask(templateId));
            JSONObject completeData = completeJo.optJSONObject("data");
            if (!MessageUtil.checkResultCode(TAG, completeJo) || completeData == null) {
                Log.other("芝麻炼金⚗️[领取任务奖励失败] raw=" + completeJo);
                return;
            }
            Log.other("芝麻炼金⚗️[领取成功] 获得芝麻粒=" + completeData.optInt("zmlNum", 0) + " 提示=" + completeData.optString("toast", ""));
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG + ".doSesameAlchemy.alchemyTimeLimitedTask", t);
        }
    }
    
    // 我的快递任务
    private void RecommendTask() {
        try {
            // 调用 AntMemberRpcCall.queryRecommendTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryRecommendTask();
            JSONObject jsonResponse = new JSONObject(response);
            // 获取 taskDetailList 数组
            JSONArray taskDetailList = jsonResponse.getJSONArray("taskDetailList");
            // 遍历 taskDetailList
            for (int i = 0; i < taskDetailList.length(); i++) {
                JSONObject taskDetail = taskDetailList.getJSONObject(i);
                // 检查 "canAccess" 的值是否为 true
                boolean canAccess = taskDetail.optBoolean("canAccess", false);
                if (!canAccess) {
                    // 如果 "canAccess" 不为 true，跳过
                    continue;
                }
                // 获取 taskMaterial 对象
                JSONObject taskMaterial = taskDetail.optJSONObject("taskMaterial");
                // 获取 taskBaseInfo 对象
                JSONObject taskBaseInfo = taskDetail.optJSONObject("taskBaseInfo");
                // 获取 taskCode
                String taskCode = taskMaterial.optString("taskCode", "");
                // 根据 taskCode 执行不同的操作
                if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode) || "WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                    if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode)) {
                        String forestTaskResponse = AntMemberRpcCall.forestTask();
                        TimeUtil.sleep(500);
                        String forestreceiveTaskAward = AntMemberRpcCall.forestreceiveTaskAward();
                    }
                    else if ("WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                        //String oceanHomePageResponse = AntMemberRpcCall.queryoceanHomePage();
                        String oceanTaskResponse = AntMemberRpcCall.oceanTask();
                        TimeUtil.sleep(500);
                        String oceanreceiveTaskAward = AntMemberRpcCall.oceanreceiveTaskAward();
                    }
                    if (taskBaseInfo != null) {
                        String appletName = taskBaseInfo.optString("appletName", "Unknown Applet");
                        Log.other("我的快递💌完成[" + appletName + "]");
                    }
                }
                if (taskMaterial == null || !taskMaterial.has("taskId")) {
                    // 如果 taskMaterial 为 null 或者不包含 taskId，跳过
                    continue;
                }
                // 获取 taskId
                String taskId = taskMaterial.getString("taskId");
                // 调用 trigger 方法
                String triggerResponse = AntMemberRpcCall.trigger(taskId);
                JSONObject triggerResult = new JSONObject(triggerResponse);
                // 检查 success 字段
                boolean success = triggerResult.getBoolean("success");
                if (success) {
                    // 从 triggerResponse 中获取 prizeSendInfo 数组
                    JSONArray prizeSendInfo = triggerResult.getJSONArray("prizeSendInfo");
                    if (prizeSendInfo.length() > 0) {
                        JSONObject prizeInfo = prizeSendInfo.getJSONObject(0);
                        JSONObject extInfo = prizeInfo.getJSONObject("extInfo");
                        // 获取 promoCampName
                        String promoCampName = extInfo.optString("promoCampName", "Unknown Promo Campaign");
                        // 输出日志信息
                        Log.other("我的快递💌完成[" + promoCampName + "]");
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.err(TAG, "RecommendTask err:", th);
        }
    }
    
    private void OrdinaryTask() {
        try {
            // 调用 AntMemberRpcCall.queryOrdinaryTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryOrdinaryTask();
            JSONObject jsonResponse = new JSONObject(response);
            // 检查是否请求成功
            if (jsonResponse.getBoolean("success")) {
                // 获取任务详细列表
                JSONArray taskDetailList = jsonResponse.getJSONArray("taskDetailList");
                // 遍历任务详细列表
                for (int i = 0; i < taskDetailList.length(); i++) {
                    // 获取当前任务对象
                    JSONObject task = taskDetailList.getJSONObject(i);
                    // 提取任务 ID、处理状态和触发类型
                    String taskId = task.optString("taskId");
                    String taskProcessStatus = task.optString("taskProcessStatus");
                    String sendCampTriggerType = task.optString("sendCampTriggerType");
                    // 检查任务状态和触发类型，执行触发操作
                    if (!"RECEIVE_SUCCESS".equals(taskProcessStatus) && !"EVENT_TRIGGER".equals(sendCampTriggerType)) {
                        // 调用 signuptrigger 方法
                        String signuptriggerResponse = AntMemberRpcCall.signuptrigger(taskId);
                        // 调用 sendtrigger 方法
                        String sendtriggerResponse = AntMemberRpcCall.sendtrigger(taskId);
                        // 解析 sendtriggerResponse
                        JSONObject sendTriggerJson = new JSONObject(sendtriggerResponse);
                        // 判断任务是否成功
                        if (sendTriggerJson.getBoolean("success")) {
                            // 从 sendtriggerResponse 中获取 prizeSendInfo 数组
                            JSONArray prizeSendInfo = sendTriggerJson.getJSONArray("prizeSendInfo");
                            // 获取 prizeName
                            String prizeName = prizeSendInfo.getJSONObject(0).getString("prizeName");
                            Log.other("我的快递💌完成[" + prizeName + "]");
                        }
                        else {
                            Log.i(TAG, "sendtrigger failed for taskId: " + taskId);
                        }
                        TimeUtil.sleep(1000);
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.err(TAG, "OrdinaryTask err:", th);
        }
    }
}
