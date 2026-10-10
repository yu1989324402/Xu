package com.surexu.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;

import com.surexu.sesame.entity.RpcEntity;
import com.surexu.sesame.hook.ApplicationHook;

import java.util.List;
import java.util.UUID;
import com.surexu.sesame.model.base.TaskAlternative;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.RandomUtil;
import com.surexu.sesame.util.StringUtil;

public class AntMemberRpcCall {

    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }

    public static Boolean check() {
        RpcEntity rpcEntity = ApplicationHook.requestObject("alipay.antmember.biz.rpc.member.h5.queryPointCert", "[{\"page\":" + 1 + ",\"pageSize\":" + 8 + "}]", 1, 0);
        return rpcEntity != null && !rpcEntity.getHasError();
    }

    /* ant member point */
    public static String queryPointCert(int page, int pageSize) {
        String args1 = "[{\"page\":" + page + ",\"pageSize\":" + pageSize + "}]";
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.member.h5.queryPointCert", args1);
    }

    public static String receivePointByUser(String certId) {
        String args1 = "[{\"certId\":" + certId + "}]";
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.member.h5.receivePointByUser", args1);
    }

    public static String queryMemberSigninCalendar() {
        return ApplicationHook.requestString("com.alipay.amic.biz.rpc.signin.h5.queryMemberSigninCalendar", "[{\"autoSignIn\":true,\"chInfo\":\"memberHomePage_ch_mytab\",\"invitorUserId\":\"\",\"sceneCode\":\"QUERY\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"mytab\",\"unid\":\"\"}}]");
    }

    /* 会员任务 */
    public static String signPageTaskList() {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.signPageTaskList", "[{\"sourceBusiness\":\"antmember\",\"spaceCode\":\"ant_member_xlight_task\"}]");
    }

    public static String applyTask(String darwinName, Long taskConfigId) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.applyTask", "[{\"darwinExpParams\":{\"darwinName\":\"" + darwinName + "\"},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"taskConfigId\":" + taskConfigId + "}]");
    }

    public static String executeTask(String bizParam, String bizSubType) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.executeTask", "[{\"bizOutNo\":\"" + (System.currentTimeMillis() - 16000L) + "\",\"bizParam\":\"" + bizParam + "\",\"bizSubType\":\"" + bizSubType + "\",\"bizType\":\"BROWSE\"}]");
    }

    public static String queryAllStatusTaskList() {
        return ApplicationHook.requestString("com.alipay.amic.memtask.h5.MemTaskListQueryFacade.queryAllStatusTaskList", "[{\"source\":\"signInAd\"}]");
    }

    /**
     * 黄金票收取
     *
     * @param str signInfo
     * @return 结果
     */
    public static String goldBillCollect(String str) {
        return ApplicationHook.requestString("com.alipay.wealthgoldtwa.goldbill.v2.index.collect", "[{" + str + "\"trigger\":\"Y\"}]");
    }

    /**
     * 黄金票收取（兼容）
     */
    public static String goldBillCollect() {
        return ApplicationHook.requestString("com.alipay.wealthgoldtwa.goldbill.v2.index.collect", "[{}]");
    }

    /**
     * 黄金票首页数据
     */
    public static String queryGoldTicketHome() {
        try {
            JSONObject args = new JSONObject();
            args.put("bizScene", "ch_alipaysearch__chsub_normal");
            args.put("chInfo", "ch_alipaysearch__chsub_normal");
            args.put("taskId", "");
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.v2.index",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 黄金票首页场景收取（新版）
     */
    public static String goldTicketIndexCollect() {
        try {
            JSONObject args = new JSONObject();
            args.put("directModeDisableCollect", 1);
            args.put("from", "antfarm");
            args.put("trigger", "Y");
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.index.collect",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 福利中心刷新
     */
    public static String welfareCenterUpdate(int modeBitMask) {
        try {
            JSONObject args = new JSONObject();
            args.put("modeBitMask", modeBitMask);
            return ApplicationHook.requestString("com.alipay.finaggexpbff.needle.welfareCenter.update",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 查询黄金票提取页信息
     * 用于获取最新的可用数量、基金ID (productId) 和 赠送份数 (bonusAmount)
     */
    public static String queryConsumeHome() {
        try {
            JSONObject args = new JSONObject();
            args.put("tabBubbleDeliverParam", new JSONObject());
            args.put("tabTypeDeliverParam", new JSONObject());
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.consume.query",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 提交提取黄金
     * @param amount 提取数量 (如 100, 200, 2900)
     * @param productId 基金ID
     * @param bonusAmount 额外赠送数量
     */
    public static String submitConsume(int amount, String productId, int bonusAmount) {
        try {
            JSONObject args = new JSONObject();
            args.put("exchangeAmount", amount);
            args.put("exchangeMoney", String.format("%.2f", amount / 1000.0));
            args.put("prizeType", "GOLD");
            args.put("productId", productId);
            args.put("bonusAmount", bonusAmount);
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.consume.submit",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 任务查询推送
     * @param taskId 任务ID
     */
    public static String taskQueryPush(String taskId) {
        try {
            JSONObject args = new JSONObject();
            args.put("mode", 1);
            args.put("taskId", taskId);
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.taskQueryPush",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 任务触发/报名
     * @param taskId 任务ID
     */
    public static String goldBillTaskTrigger(String taskId) {
        try {
            JSONObject args = new JSONObject();
            args.put("taskId", taskId);
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.goldbill.v4.task.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 福利中心首页
     */
    public static String queryWelfareHome() {
        try {
            JSONObject args = new JSONObject();
            args.put("isResume", true);
            return ApplicationHook.requestString("com.alipay.finaggexpbff.needle.welfareCenter.index",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 签到 / 领取奖励
     * @param type "SIGN"
     */
    public static String welfareCenterTrigger(String type) {
        try {
            JSONObject args = new JSONObject();
            args.put("type", type);
            return ApplicationHook.requestString("com.alipay.finaggexpbff.needle.welfareCenter.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 游戏中心签到查询
     */
    public static String querySignInBall() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.querySignInBall", "[{\"source\":\"ch_appcenter__chsub_9patch\"}]");
    }

    /**
     * 游戏中心签到
     */
    public static String continueSignIn() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.continueSignIn", "[{\"sceneId\":\"GAME_CENTER\",\"signType\":\"NORMAL_SIGN\"}]");
    }

    /**
     * 游戏中心查询待领取乐豆列表
     */
    public static String queryPointBallList() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", "[{\"source\":\"ch_appcenter__chsub_9patch\"}]");
    }

    /**
     * 游戏中心全部领取
     */
    public static String batchReceivePointBall() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall", "[{}]");
    }
    
    public static String doTaskSignup(String taskId) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSignup", "[{\"taskId\":\"" + taskId + "\"}]");
    }
    
    public static String doTaskSend(String taskId) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSend", "[{\"taskId\":\"" + taskId + "\"}]");
    }
    
    public static String queryModularTaskList() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryModularTaskList", "[{\"deviceLevel\":\"high\",\"source\":\"ch_appcollect__chsub_my-recentlyUsed\",\"sourceTab\":\"luckydraw\",\"unityDeviceLevel\":\"high\"}]");
    }
    
    /**
     * 游戏中心（会员场景 xlyy_WJCNJT）首页：任务进度与是否有待领奖励
     */
    public static String gameCenterHomePage() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.queryHomePage",
                "[{\"__git\":\"9e159d58cce04c13a\",\"channelTaskPassThrough\":\"\",\"deviceLevel\":\"high\",\"guideType\":\"\",\"moduleId\":\"\",\"sceneId\":\"xlyy_WJCNJT\",\"source\":\"zfbhy_mc_wkenoz66\",\"unityDeviceLevel\":\"high\"}]");
    }

    /**
     * 游戏中心任务流（feedsList 里是「去玩某个游戏」的任务）
     */
    public static String gameCenterGameFeeds(int pageNum, int pageSize) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.queryGameFeeds",
                "[{\"__git\":\"9e159d58cce04c13a\",\"deviceLevel\":\"high\",\"filterGameIdList\":[],\"pageNum\":" + pageNum
                        + ",\"pageSize\":" + pageSize
                        + ",\"sceneId\":\"xlyy_WJCNJT\",\"source\":\"zfbhy_mc_wkenoz66\",\"unityDeviceLevel\":\"high\"}]");
    }

    /**
     * 游戏中心一键领取任务奖励（整场景批量，无需任务 id）
     */
    public static String batchReceiveTaskPrize() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.batchReceiveTaskPrize",
                "[{\"__git\":\"9e159d58cce04c13a\",\"deviceLevel\":\"high\",\"sceneId\":\"xlyy_WJCNJT\",\"source\":\"zfbhy_mc_wkenoz66\",\"unityDeviceLevel\":\"high\"}]");
    }
    
    public static String queryTaskList() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v4.queryTaskList", "[{\"__git\":\"52f2c9969ae\",\"source\":\"ch_alipaysearch__chsub_normal\"}]");
    }
    
    /**
     * 查询可收取的芝麻粒
     *
     * @return 结果
     */
    public static String queryCreditFeedback() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.queryCreditFeedback", "[{\"queryPotential\":false,\"size\":20,\"status\":\"UNCLAIMED\"}]");
    }

    /**
     * 芝麻信用首页
     *
     * @return 结果
     */
    public static String queryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryHome",
                "[{\"invokeSource\":\"zmHome\",\"miniZmGrayInside\":\"\",\"version\":\"week\"}]");
    }

    /**
     * 查询芝麻分进度任务列表
     * @return RPC响应
     */
    public static String queryGrowthBehaviorToDoList() {
        String requestData = "[{\"guideBehaviorId\":\"yuebao_7d\",\"invokeVersion\":\"1.0.2025.10.27\",\"switchNewPage\":true}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.queryToDoList", requestData);
    }

    /**
     * 接受/开启一个行为任务
     * @param behaviorId 任务ID
     * @return RPC响应
     */
    public static String openBehaviorCollect(String behaviorId) {
        String requestData = "[{\"behaviorId\":\"" + behaviorId + "\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.openBehaviorCollect", requestData);
    }

    /**
     * 查询视频答题的题目信息
     * @return RPC响应
     */
    public static String queryDailyQuiz(String behaviorId) {
        String requestData = "[{\"behaviorId\":\""+behaviorId+"\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.queryDailyQuiz", requestData);
    }

    /**
     * 提交视频答题的答案
     * @param bizDate 从queryDailyQuiz获取的bizDate
     * @param questionId 问题ID
     * @param answerId 选择的答案ID
     * @return RPC响应
     */
    public static String pushDailyQuizAnswer(String behaviorId,long bizDate,String answerId, String questionId, String answerStatus) {
        String extInfo = "{\"answerId\":\"" + answerId + "\",\"answerStatus\":\""+answerStatus+"\",\"questionId\":\"" + questionId + "\"}";
        String requestData = "[{\"behaviorId\":\""+behaviorId+"\",\"bizDate\":" + bizDate + ",\"extInfo\":" + extInfo + "}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.pushDailyTask", requestData);
    }

    /**
     * 查询当前可领取的进度球
     * @return RPC响应
     */
    public static String queryScoreProgress() {
        String requestData = "[{\"needTotalProcess\":\"TRUE\",\"queryGuideInfo\":true,\"switchNewPage\":true}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryScoreProgress", requestData);
    }

    /**
     * 收集一个或多个进度球
     * @param ballIdList 包含一个或多个进度球ID的JSONArray
     * @return RPC响应
     */
    public static String collectProgressBall(JSONArray ballIdList) {
        if (ballIdList == null || ballIdList.length() == 0) {
            return "{\"success\":false, \"resultView\":\"ballIdList为空\"}";
        }
        String requestData = "[{\"ballIdList\":" + ballIdList.toString() + "}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.collectProgressBall", requestData);
    }

    /**
     * 收取芝麻粒
     *
     * @param creditFeedbackId creditFeedbackId
     * @return 结果
     */
    //{"chInfo":"ch_zhimahome__chsub_zml_doudi","deliverStatus":"","deliveryTemplateId":"","sceneCode":"DAILY_MUST_DO_CARD","searchAddToHomeTask":true,"searchGuidePopFlag":true,"searchShareAssistTask":true,"searchSubscribeTask":true,"version":"new"}]}
    public static String CreditAccumulateStrategyRpcManager(String creditFeedbackId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", "[{\"chInfo\":\"ch_zhimahome__chsub_zml_doudi\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"sceneCode\":\"DAILY_MUST_DO_CARD\"," +
                "\"searchAddToHomeTask\":true,\"searchGuidePopFlag\":true,\"searchShareAssistTask\":true,\"searchSubscribeTask\":true,\"version\":\"new\"}]");
    }

    public static String collectCreditFeedback(String creditFeedbackId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback", "[{\"collectAll\":false,\"creditFeedbackId\":\"" + creditFeedbackId + "\",\"status\":\"UNCLAIMED\"}]");
    }

    /**
     * 查询生活记录
     *
     * @return 结果
     */
    public static String promiseQueryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.queryHome", null);
    }

    public static String querySingleTemplate(String templateId) {
        String args = "[{\"templateId\":\"" + templateId + "\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.querySingleTemplate", args);
    }

    public static String promiseJoin(JSONObject data) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.join", "[" + data + "]");
    }

    /**
     * 查询生活记录明细
     *
     * @param recordId recordId
     * @return 结果
     */
    public static String promiseQueryDetail(String recordId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.queryDetail", "[{\"recordId\":\"" + recordId + "\"}]");
    }

    /**
     * 查询会员积分兑换福利列表方法1
     *
     * @param userId     userId
     * @param deliveryId 分类码
     *                   94000SR2023102305988003: 0元起
     *                   94000SR2024011106752003: 0元起/公益道具
     *                   94000SR2024071108523003: 0元起/皮肤
     *                   94000SR2024071808609003: 皮肤
     * @return 分类下商品列表
     * @ param naviCode 导航分类码
     * 皮肤："bb82b"、0元起："全积分"、影音："13"
     */
    public static String queryDeliveryZoneDetail(String userId, String deliveryId) {
        String uniqueId = System.currentTimeMillis() + "全积分0and99999999INTELLIGENT_SORT" + userId;
        String args = "[{\"cityCode\":\"\",\"deliveryId\":\"" + deliveryId + "\",\"pageNum\":1,\"pageSize\":18,\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"topIdList\":[],\"uniqueId\":\"" + uniqueId + "\"}]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryDeliveryZoneDetail", args);
    }

    /**
     * 查询会员积分兑换福利列表方法2
     *
     * @param userId   userId
     * @param naviCode 导航分类码
     *                 特色："14"、出行："1"、美食："11"、日用："12"、上新：""
     * @return 分类下商品列表
     */
    public static String queryIndexNaviBenefitFlowV2(String userId, String naviCode, int pageNum) {
        String sortStrategy = "INTELLIGENT_SORT";
        String upperPoint = "99999999";
        String uniqueId = System.currentTimeMillis() + naviCode + "0and" + upperPoint + sortStrategy + userId;
        String args =
                "[\n" + "        {\n" + "            \"adCopyId\": \"\",\n" + "            \"benefitFlowSource\": \"REC\",\n" + "            \"cityCode\": \"\",\n" + "            \"excludeIds\": \"\",\n" + "            \"exposeChannel\": \"antmember\",\n" + "            \"fastTag\": \"\"," + "\n" + "            \"lowerPoint\": 0,\n" + "            \"naviCode\": \"" + naviCode + "\",\n" + "            \"pageNum\": " + pageNum + ",\n" + "            \"pageSize\": 50,\n" + "            \"requestSourceInfo\": \"-|feeds\",\n" + "            \"sortStrategy\": \"" + sortStrategy + "\",\n" + "            \"sourcePassMap\": {\n" + "                \"innerSource\": \"\",\n" + "                \"source\": \"myTab\",\n" + "                \"unid\": \"\"\n" + "            },\n" + "            \"stickyIdList\": [],\n" + "            \"tagCodeIdx\": -1,\n" + "            \"uniqueId\": \"" + uniqueId + "\",\n" + "            \"upperPoint\": " + upperPoint + ",\n" + "            \"withPointRange\": false\n" + "        }\n" + "    ]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryIndexNaviBenefitFlowV2", args);
    }

    /**
     * 会员积分兑换福利
     *
     * @param benefitId benefitId
     * @param itemId    itemId
     * @return 结果
     */
    public static String exchangeBenefit(String benefitId, String itemId) {
        String requestId = "requestId" + System.currentTimeMillis();
        String alipayClientVersion = ApplicationHook.getAlipayVersion().getVersionString();
        String args =
                "[{\"benefitId\":\"" + benefitId + "\",\"cityCode\":\"\",\"exchangeType\":\"POINT_PAY\",\"itemId\":\"" + itemId + "\",\"miniAppId\":\"\",\"orderSource\":\"\",\"requestId\":\"" + requestId + "\",\"requestSourceInfo\":\"\",\"sourcePassMap\":{\"alipayClientVersion\":\"" + alipayClientVersion + "\",\"innerSource\":\"\",\"mobileOsType\":\"Android\",\"source\":\"\",\"unid\":\"\"},\"userOutAccount\":\"\"}]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", args);
    }

    /**
     * 会员积分兑换（带支付策略）
     *
     * @param benefitId    权益ID
     * @param itemId       商品ID
     * @param strategyType POINT_PAY / POINT_CASH_PAY
     * @return 结果
     */
    public static String exchangeMemberBenefit(String benefitId, String itemId, String strategyType) {
        String requestId = "requestId" + System.currentTimeMillis();
        String alipayClientVersion = ApplicationHook.getAlipayVersion().getVersionString();
        String exchangeType = "POINT_PAY".equals(strategyType) ? "POINT_PAY" : "POINT_CASH_PAY";
        String args =
                "[{\"benefitId\":\"" + benefitId + "\",\"cityCode\":\"510100\",\"exchangeType\":\"" + exchangeType + "\",\"itemId\":\"" + itemId + "\",\"miniAppId\":\"\",\"orderSource\":\"\",\"requestId\":\"" + requestId + "\",\"requestSourceInfo\":\"SID:|5\",\"sourcePassMap\":{\"alipayClientVersion\":\"" + alipayClientVersion + "\",\"innerSource\":\"a159.b52659\",\"mobileOsType\":\"Android\",\"source\":\"\",\"unid\":\"" + UUID.randomUUID().toString() + "\"},\"userOutAccount\":\"\"}]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", args);
    }

    /**
     * 查询会员信息（积分余额）
     * @return 结果，data.pointBalance 为可用积分
     */
    public static String queryMemberInfo() {
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.member.h5.queryMemberInfo", "[{\"needGrade\":true,\"needPoint\":true,\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]");
    }

    /**
     * 按关键词搜索会员权益
     */
    public static String searchMemberBenefit(String keyword, int pageNo, int pageSize) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("cityCode", "510100");
            obj.put("clientOs", "Android");
            obj.put("clientVersion", "12.12.20.8000");
            obj.put("pageNum", pageNo);
            obj.put("pageSize", pageSize);
            obj.put("paramsMap", new JSONObject());
            obj.put("prePageAllZeroStock", false);
            obj.put("previewCopyDbId", "");
            obj.put("query", keyword);
            obj.put("recommend", false);
            obj.put("searchId", String.valueOf(System.currentTimeMillis()));
            obj.put("sessionId", "session_" + System.currentTimeMillis());
            JSONObject spm = new JSONObject();
            spm.put("innerSource", "");
            spm.put("source", "");
            spm.put("unid", "");
            obj.put("sourcePassMap", spm);
            JSONArray arr = new JSONArray();
            arr.put(obj);
            return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.benefitSearchV2", arr.toString());
        } catch (Throwable t) {
            Log.printStackTrace("AntMemberRpcCall", t);
            return "{}";
        }
    }

    /**
     * 查询单个权益详情
     */
    public static String querySingleBenefitDetail(String benefitId) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("benefitId", benefitId);
            obj.put("cityCode", "510100");
            obj.put("miniAppId", "");
            obj.put("requestSourceInfo", "SID:|5");
            JSONObject spm = new JSONObject();
            spm.put("innerSource", "a159.b52659");
            spm.put("source", "");
            spm.put("unid", UUID.randomUUID().toString());
            obj.put("sourcePassMap", spm);
            JSONArray arr = new JSONArray();
            arr.put(obj);
            return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.querySingleBenefitDetail", arr.toString());
        } catch (Throwable t) {
            Log.printStackTrace("AntMemberRpcCall", t);
            return "{}";
        }
    }

    /**
     * 查询下单确认信息
     */
    public static String queryPromoBenefitOrderConfirmInfo(String benefitId) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("benefitId", benefitId);
            obj.put("requestSourceInfo", "SID:|5");
            JSONObject spm = new JSONObject();
            spm.put("innerSource", "a159.b52659");
            spm.put("source", "");
            spm.put("unid", UUID.randomUUID().toString());
            obj.put("sourcePassMap", spm);
            JSONArray arr = new JSONArray();
            arr.put(obj);
            return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryPromoBenefitOrderConfirmInfo", arr.toString());
        } catch (Throwable t) {
            Log.printStackTrace("AntMemberRpcCall", t);
            return "{}";
        }
    }

    /**
     * 查询导航分类（无参）
     *
     * @return 结果，data.naviInfoList 为分类列表
     */
    public static String queryNavigationWithFilter() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("exposeChannel", "antmember");
            JSONArray arr = new JSONArray();
            arr.put(obj);
            return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryNavigationWithFilter", arr.toString());
        } catch (Throwable t) {
            Log.printStackTrace("AntMemberRpcCall", t);
            return "{}";
        }
    }

    /**
     * 查询单个分类下的权益列表
     */
    public static String queryDeliveryZoneDetail(String deliveryId) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("deliveryId", deliveryId);
            obj.put("cityCode", "510100");
            JSONArray arr = new JSONArray();
            arr.put(obj);
            return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryDeliveryZoneDetail", arr.toString());
        } catch (Throwable t) {
            Log.printStackTrace("AntMemberRpcCall", t);
            return "{}";
        }
    }

    /**
     * 查询导航权益流
     *
     * @param naviCode      分类码，可为空
     * @param withPointRange 是否按积分区间过滤
     * @param lowerPoint    积分下限
     * @param upperPoint    积分上限
     * @param pageNum       页码
     * @param pageSize      每页条数
     */
    public static String queryIndexNaviBenefitFlowV2(String naviCode, boolean withPointRange, int lowerPoint, int upperPoint, int pageNum, int pageSize) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("exposeChannel", "antmember");
            obj.put("naviCode", naviCode == null ? "" : naviCode);
            obj.put("benefitFlowSource", "REC");
            obj.put("withPointRange", withPointRange);
            obj.put("lowerPoint", lowerPoint);
            obj.put("upperPoint", upperPoint);
            obj.put("pageNum", pageNum);
            obj.put("pageSize", pageSize);
            obj.put("cityCode", "510100");
            obj.put("sortStrategy", "INTELLIGENT_SORT");
            obj.put("requestSourceInfo", "-|feeds");
            JSONObject spm = new JSONObject();
            spm.put("source", "");
            spm.put("passInfo", "");
            spm.put("dhNewItem", "true");
            obj.put("sourcePassMap", spm);
            JSONArray arr = new JSONArray();
            arr.put(obj);
            return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryIndexNaviBenefitFlowV2", arr.toString());
        } catch (Throwable t) {
            Log.printStackTrace("AntMemberRpcCall", t);
            return "{}";
        }
    }

    /** 全积分区间第 1 页 */
    public static String queryIndexNaviBenefitFlowV2(int pageNum, int pageSize) {
        return queryIndexNaviBenefitFlowV2("", false, 0, 99999999, pageNum, pageSize);
    }

    /**
     * 查询闪蝶专区实体列表
     *
     * @param deliveryIds deliveryId 列表（与 entityIds 二选一）
     * @param entityIds   实体ID列表（与 deliveryIds 二选一）
     * @param topIds      置顶ID列表，可为空
     * @param pageNum     页码
     * @param pageSize    每页条数
     */
    public static String queryShandieEntityList(List<String> deliveryIds, List<String> entityIds, List<String> topIds, int pageNum, int pageSize) {
        try {
            JSONObject obj = new JSONObject();
            if (deliveryIds != null && !deliveryIds.isEmpty()) {
                obj.put("queryType", "DELIVERY_ID_LIST");
                JSONArray arr = new JSONArray();
                for (String s : deliveryIds) {
                    arr.put(s);
                }
                obj.put("deliveryIdList", arr);
            } else if (entityIds != null && !entityIds.isEmpty()) {
                obj.put("queryType", "ENTITY_ID_LIST");
                JSONArray arr = new JSONArray();
                for (String s : entityIds) {
                    arr.put(s);
                }
                obj.put("entityIdList", arr);
            }
            if (topIds != null && !topIds.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String s : topIds) {
                    arr.put(s);
                }
                obj.put("topIds", arr);
            }
            obj.put("cityCode", "510100");
            obj.put("pageNum", pageNum);
            obj.put("pageSize", pageSize);
            obj.put("filterStockNoEnough", false);
            obj.put("filterPointNoEnough", false);
            obj.put("filterExchangeTime", false);
            obj.put("filterTimesLimit", false);
            obj.put("filterTimesLimitForPromo", false);
            JSONArray arr = new JSONArray();
            arr.put(obj);
            return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryShandieEntityList", arr.toString());
        } catch (Throwable t) {
            Log.printStackTrace("AntMemberRpcCall", t);
            return "{}";
        }
    }

    // 我的快递任务
    public static String queryRecommendTask() {
        String args1 = "[{\"consultAccessFlag\":true,\"extInfo\":{\"componentCode\":\"musi_test\"},\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iCCWtq%2FRt4kh\"}]";
        return ApplicationHook.requestString("alipay.promoprod.task.listQuery", args1);
    }

    // 积分、肥料
    public static String trigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"send\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }

    // 森林活力值
    public static String queryforestHomePage() {
        String args1 = "[{\"activityParam\":{},\"configVersionMap\":{\"wateringBubbleConfig\":\"0\"},\"skipWhackMole\":false,\"source\":\"kuaidivitality\",\"version\":\"20240606\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryHomePage", args1);
    }

    public static String forestTask() {
        String args1 = "[{\"extend\":{\"firstTaskType\":\"KUAIDI_VITALITY\"},\"fromAct\":\"home_task_list\",\"source\":\"kuaidivitality\",\"version\":\"20240105\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTaskList", args1);
    }

    public static String forestreceiveTaskAward() {
        String args1 = "[{\"ignoreLimit\":false,\"requestType\":\"H5\",\"sceneCode\":\"ANTFOREST_VITALITY_TASK\",\"source\":\"ANTFOREST\",\"taskType\":\"KUAIDI_VITALITY\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", args1);
    }

    // 海洋碎片
    public static String queryoceanHomePage() {
        String args1 = "[{\"firstTaskType\":\"DAOLIU_WODEKUAIDIQUANYI\",\"source\":\"wodekuaidiquanyi\",\"uniqueId\":\"" + getUniqueId() + "\",\"version\":\"20240115\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.queryHomePage", args1);
    }

    public static String oceanTask() {
        String args1 = "[{\"extend\":{\"firstTaskType\":\"DAOLIU_WODEKUAIDIQUANYI\"},\"fromAct\":\"dynamic_task\",\"sceneCode\":\"ANTOCEAN_TASK\",\"source\":\"wodekuaidiquanyi\",\"uniqueId\":\"" + getUniqueId() + "\",\"version\":\"20240115\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.queryTaskList", args1);
    }

    public static String oceanreceiveTaskAward() {
        String args1 = "[{\"ignoreLimit\":false,\"requestType\":\"RPC\",\"sceneCode\":\"ANTOCEAN_TASK\",\"source\":\"ANT_FOREST\",\"taskType\":\"DAOLIU_WODEKUAIDIQUANYI\",\"uniqueId\":\"" + getUniqueId() + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", args1);
    }

    // 普通任务
    public static String queryOrdinaryTask() {
        String args1 = "[{\"consultAccessFlag\":true,\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\"}]";
        return ApplicationHook.requestString("alipay.promoprod.task.listQuery", args1);
    }

    public static String signuptrigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"signup\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }

    public static String sendtrigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"send\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }

    /**
     * 芝麻签到 - 通用完成接口（芝麻粒/炼金等）
     * 对应: com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask
     *
     * @param checkInDate yyyyMMdd
     * @param sceneCode   "zml" 对应芝麻粒福利签到, "alchemy" 对应芝麻炼金签到
     */
    public static String zmCheckInCompleteTask(String checkInDate, String sceneCode) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask", "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"" + sceneCode + "\"}]");
    }

    /**
     * 获取芝麻信用任务列表
     */
    public static String CreditAccumulateStrategyRpcManager() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", "[{}]");
    }

    /**
     * 芝麻信用领取任务
     */
    public static String joinSesameTask(String taskTemplateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity", "[{\"chInfo\":\"seasameList\",\"joinFromOuter\":false,\"sceneCode\":\"zml\",\"templateId\":\"" + taskTemplateId + "\"}]");
    }

    /**
     * 芝麻信用获取任务回调
     */
    public static String feedBackSesameTask(String taskTemplateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback", "[{\"actionType\":\"TO_COMPLETE\",\"templateId\":\"" + taskTemplateId + "\"}]");
    }

    /**
     * 芝麻信用 pushActivity：须带 recordId 推完记录；extCheck 通常传空。
     */
    public static String finishSesameTask(String recordId, String extCheck) {
        String body = StringUtil.isEmpty(extCheck)
                ? "[{\"recordId\":\"" + recordId + "\"}]"
                : "[{\"promiseActivityExtCheck\":\"" + extCheck + "\",\"recordId\":\"" + recordId + "\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity", body);
    }

    /**
     * 查询可收取的芝麻粒
     */

    /**
     * 一键收取芝麻粒
     */
    public static String collectAllCreditFeedback() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback", "[{\"collectAll\":true,\"status\":\"UNCLAIMED\"}]");
    }

    public static String alchemyQueryCheckIn(String scenecode) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists", "[{\"sceneCode\":\"" + scenecode + "\",\"version\":\"2025-10-22\"}]");
    }

    // ==================== 新增芝麻信用相关RPC方法 ====================

    /**
     * 芝麻信用-查询签到领粒任务列表
     * @return RPC调用结果字符串
     */
    public static String checkInQueryTaskLists() {
        String requestData = "[{\"version\":\"2025-10-22\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists", requestData);
    }

    /**
     * 芝麻信用-完成签到领粒任务
     * @param checkInDate 签到日期，格式为 "yyyyMMdd"
     * @return RPC调用结果字符串
     */
    public static String checkInCompleteTask(String checkInDate) {
        String requestData = "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"zml\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask", requestData);
    }

    /**
     * 获取芝麻信用任务列表
     */
    public static String queryAvailableSesameTask() {
        String requestData = "[{\"chInfo\":\"ch_zmxy_zmlsy__chsub_zmsy_jingangwei_lianjin\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"sceneCode\":\"DAILY_MUST_DO_CARD\",\"searchGuidePopFlag\":true,\"searchSubscribeTask\":true,\"version\":\"new\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", requestData);
    }

    /**
     * 芝麻信用任务反馈：受理，不等于完成；报文 bizType 固定为 LIFE_RECORD。
     */
    public static String feedBackSesameTaskNew(String taskTemplateId) {
        String requestData = "[{\"actionType\":\"TO_COMPLETE\",\"bizType\":\"LIFE_RECORD\""
                + ",\"sceneCode\":\"zml\",\"templateId\":\"" + taskTemplateId + "\",\"version\":\"new\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback", requestData);
    }

    /**
     * 查询「最近一次操作任务」：join 被 {@code PROMISE_HAS_PROCESSING_TEMPLATE}（存在进行中的生活记录）拒绝时，
     * 用它取回那条记录的 {@code recordId} 继续推完。
     */
    public static String queryLastOperateTask() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryLastOperateTask", "[{\"version\":\"new\"}]");
    }

    /**
     * 另一种实现方案的 version：本模块**原有取值**，收敛到 {@link TaskAlternative} 时原样保留，不改已实测路径的报文。
     */
    public static final String DO_FARM_TASK_VERSION = "20250812.01";

    /**
     * 另一种实现方案：按 bizKey 完成任务（{@code com.alipay.antfarm.doFarmTask}）。
     * <p>游戏中心任务与庄园抽抽乐、芭芭农场、金豆乐园同源：`doTaskSend` 常被服务端以
     * 400000040「不支持rpc调用」拒绝，而这条接口能把它们做成（2026-09-22 在三处实测通过）。
     * <p>它的响应**不可信**（可能回 102「服务器正在开小差」而任务其实已生效），
     * 调用方必须用任务列表状态核对，不能据响应判成败。version 经实测不被校验，这里沿用本模块原值。
     */
    public static String doFarmTask(String bizKey, String taskSceneCode) {
        return TaskAlternative.request(bizKey, taskSceneCode, DO_FARM_TASK_VERSION);
    }

    /**
     * 芝麻炼金 - 查询主页信息
     */
    public static String alchemyQueryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryHome", "[{}]");
    }

    /**
     * 芝麻炼金 - 查询攒粒日常任务列表
     */
    public static String alchemyQueryTasks() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3",
                "[{\"chInfo\":\"\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"searchSubscribeTask\":true,\"version\":\"alchemy\"}]");
    }

    /**
     * 芝麻炼金 - 查询签到任务状态
     */
    public static String alchemyQueryCheckInTasks() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists",
                "[{\"sceneCode\":\"alchemy\",\"version\":\"2025-10-22\"}]");
    }

    /**
     * 芝麻炼金 - 完成签到任务
     * @param checkInDate YYYYMMDD格式的日期字符串
     */
    public static String completeAlchemyCheckIn(String checkInDate) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask",
                "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"alchemy\"}]");
    }

    /**
     * 芝麻炼金 - 领取奖励 (用于领取次日礼包)
     */
    public static String alchemyClaimAward() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.claimAward", "[{}]");
    }

    /**
     * 芝麻炼金 - 查询限时任务(早/中/晚饭)
     */
    public static String alchemyQueryTimeLimitedTask() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask", "[{}]");
    }

    /**
     * 芝麻炼金 - 完成限时任务(早/中/晚饭)
     * @param templateId 任务模板ID
     */
    public static String alchemyCompleteTimeLimitedTask(String templateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask",
                "[{\"templateId\":\"" + templateId + "\"}]");
    }

    /**
     * 芝麻炼金 - 执行炼金动作
     */
    public static String doAlchemy() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.alchemy", "[null]");
    }

    /**
     * 芝麻树通用触发器
     * @param operation 操作类型
     * @param extInfoJson 额外信息JSON字符串
     * @return RPC响应
     */
    private static String sesameTreeTrigger(String operation, String extInfoJson) {
        String playInfo = "SwbtxJSo8OOUrymAU%2FHnY2jyFRc%2BkCJ3";
        String refer = "https://render.alipay.com/p/yuyan/180020010001269849/zmTree.html?caprMode=sync&chInfo=chInfo=ch_zmzltf__chsub_xinyongsyyingxiaowei";
        String requestData = String.format("[{\"operation\":\"%s\",\"playInfo\":\"%s\",\"refer\":\"%s\",\"extInfo\":%s}]",
                operation, playInfo, refer, extInfoJson);
        return ApplicationHook.requestString("alipay.promoprod.play.trigger", requestData);
    }

    /**
     * 获取芝麻树主页信息
     * @return RPC响应
     */
    public static String getSesameTreeHomePage() {
        return sesameTreeTrigger("ZHIMA_TREE_HOME_PAGE", "{}");
    }

    /**
     * 获取芝麻树任务列表
     * @return RPC响应
     */
    public static String getSesameTreeTaskList() {
        String extInfo = "{\"batchId\":\"\",\"chInfo\":\"ch_zmzltf__chsub_xinyongsyyingxiaowei\"}";
        return sesameTreeTrigger("RENT_GREEN_TASK_LIST_QUERY", extInfo);
    }

    /**
     * 净化芝麻树（通过点击按钮，消耗净化次数）
     * @return RPC响应
     */
    public static String cleanSesameTreeByClick() {
        String extInfo = "{\"clickNum\":\"1\",\"treeCode\":\"ZHIMA_TREE\"}";
        return sesameTreeTrigger("ZHIMA_TREE_CLEAN_AND_PUSH", extInfo);
    }

    /**
     * 完成芝麻树任务
     * @param taskId 任务ID
     * @return RPC响应
     */
    public static String finishSesameTreeTask(String taskId) {
        String chInfo = "ch_zmzltf__chsub_xinyongsyyingxiaowei";
        String extInfo = String.format(
                "{\"chInfo\":\"%s\",\"stageCode\":\"send\",\"taskId\":\"%s\"}",
                chInfo, taskId);
        return sesameTreeTrigger("RENT_GREEN_TASK_FINISH", extInfo);
    }

    /**
     * 领取芝麻树任务奖励
     * @param taskId 任务ID
     * @return RPC响应
     */
    public static String receiveSesameTreeTaskReward(String taskId) {
        String chInfo = "ch_zmzltf__chsub_xinyongsyyingxiaowei";
        String extInfo = String.format(
                "{\"chInfo\":\"%s\",\"stageCode\":\"receive\",\"taskId\":\"%s\"}",
                chInfo, taskId);
        return sesameTreeTrigger("RENT_GREEN_TASK_FINISH", extInfo);
    }

    // ================= 保障金相关RPC方法 =================

    /**
     * 获取所有可领取的保障金
     */
    public static String queryAvailableCollectInsuredGold() {
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.queryMultiSceneWaitToGainList",
                "[{\"entrance\":\"wealth_entry\",\"eventToWaitParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]},\"helpChildParamDTO\":{\"giftProdCode\":\"GIFT_HEALTH_GOLD_CHILD\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]},\"priorityChannelParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]},\"signInParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]}}]");
    }

    /**
     * 领取保障金
     */
    public static String collectInsuredGold(JSONObject goldBallObj) {
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.gainMyAndFamilySumInsured",
                goldBallObj.toString());
    }

    // ================= 安心豆相关RPC方法 =================

    /**
     * 安心豆签到查询
     */
    public static String querySignInProcess(String appletId, String scene) {
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.querySignInProcess",
                "[{\"appletId\":\"" + appletId + "\",\"scene\":\"" + scene + "\"}]");
    }

    /**
     * 安心豆签到触发
     */
    public static String signInTrigger(String appletId, String scene) {
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.signInTrigger",
                "[{\"appletId\":\"" + appletId + "\",\"scene\":\"" + scene + "\"}]");
    }

    /**
     * 安心豆兑换详情查询
     */
    public static String beanExchangeDetail(String itemId) {
        return ApplicationHook.requestString("com.alipay.insmarketingbff.onestop.planTrigger",
                "[{\"extParams\":{\"itemId\":\"" + itemId + "\"}," +
                        "\"planCode\":\"bluebean_onestop\",\"planOperateCode\":\"exchangeDetail\"}]");
    }

    /**
     * 安心豆兑换
     */
    public static String beanExchange(String itemId, int pointAmount) {
        return ApplicationHook.requestString("com.alipay.insmarketingbff.onestop.planTrigger",
                "[{\"extParams\":{\"itemId\":\"" + itemId + "\",\"pointAmount\":\"" + Integer.toString(pointAmount) + "\"}," +
                        "\"planCode\":\"bluebean_onestop\",\"planOperateCode\":\"exchange\"}]");
    }

    /**
     * 查询用户账户信息
     */
    public static String queryUserAccountInfo(String pointProdCode) {
        return ApplicationHook.requestString("com.alipay.insmarketingbff.point.queryUserAccountInfo",
                "[{\"channel\":\"HiChat\",\"pointProdCode\":\"" + pointProdCode + "\",\"pointUnitType\":\"COUNT\"}]");
    }

    // ================= 年度回顾相关RPC方法 =================

    public static final String ANNUAL_REVIEW_OPERATION_IDENTIFY =
            "independent_component_program2025111803036407";
    public static final String ANNUAL_REVIEW_COMPONENT_PREFIX =
            "independent_component_task_reward_v2_02888775";
    public static final String ANNUAL_REVIEW_QUERY_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_query";
    public static final String ANNUAL_REVIEW_APPLY_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_apply";
    public static final String ANNUAL_REVIEW_PROCESS_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_process";
    public static final String ANNUAL_REVIEW_GET_REWARD_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_get_reward";

    /**
     * 年度回顾 - 查询任务列表
     */
    public static String annualReviewQueryTasks() {
        try {
            JSONObject body = new JSONObject();
            body.put("channel", "share");
            body.put("cityCode", "110000");
            body.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
            body.put("source", ANNUAL_REVIEW_QUERY_COMPONENT);

            JSONObject components = new JSONObject();
            components.put(ANNUAL_REVIEW_QUERY_COMPONENT, new JSONObject());
            body.put("components", components);

            return ApplicationHook.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 领取单个任务（apply）
     */
    public static String annualReviewApplyTask(String code) {
        try {
            JSONObject body = new JSONObject();
            body.put("channel", "share");
            body.put("cityCode", "110000");
            body.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
            body.put("source", ANNUAL_REVIEW_APPLY_COMPONENT);

            JSONObject compBody = new JSONObject();
            compBody.put("code", code);
            compBody.put("consultAfterLuckDraw", "false");
            compBody.put("skipLuckDrawConsult", "true");

            JSONObject components = new JSONObject();
            components.put(ANNUAL_REVIEW_APPLY_COMPONENT, compBody);

            body.put("components", components);

            return ApplicationHook.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 提交任务完成（process）
     */
    public static String annualReviewProcessTask(String code, String recordNo) {
        try {
            JSONObject body = new JSONObject();
            body.put("channel", "share");
            body.put("cityCode", "110000");
            body.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
            body.put("source", ANNUAL_REVIEW_PROCESS_COMPONENT);

            JSONObject compBody = new JSONObject();
            compBody.put("code", code);
            compBody.put("recordNo", recordNo);

            JSONObject components = new JSONObject();
            components.put(ANNUAL_REVIEW_PROCESS_COMPONENT, compBody);

            body.put("components", components);

            return ApplicationHook.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 领取奖励（get_reward）
     */
    public static String annualReviewGetReward(String code, String recordNo) {
        try {
            JSONObject body = new JSONObject();
            body.put("channel", "share");
            body.put("cityCode", "110000");
            body.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
            body.put("source", ANNUAL_REVIEW_GET_REWARD_COMPONENT);

            JSONObject compBody = new JSONObject();
            compBody.put("code", code);
            compBody.put("consultAfterLuckDraw", "false");
            compBody.put("recordNo", recordNo);
            compBody.put("skipLuckDrawConsult", "true");

            JSONObject components = new JSONObject();
            components.put(ANNUAL_REVIEW_GET_REWARD_COMPONENT, compBody);

            body.put("components", components);

            return ApplicationHook.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

}
