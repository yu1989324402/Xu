package com.surexu.sesame.model.task.antForest;

import static com.surexu.sesame.model.normal.base.BaseModel.taskRpcRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.surexu.sesame.util.XHelpers;
import com.surexu.sesame.data.ConfigV2;
import com.surexu.sesame.data.ModelField;
import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.RuntimeInfo;
import com.surexu.sesame.data.TokenConfig;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.modelFieldExt.ChoiceModelField;
import com.surexu.sesame.data.modelFieldExt.EmptyModelField;
import com.surexu.sesame.data.modelFieldExt.IntegerModelField;
import com.surexu.sesame.data.modelFieldExt.ListModelField;
import com.surexu.sesame.data.modelFieldExt.SelectAndCountModelField;
import com.surexu.sesame.data.modelFieldExt.SelectModelField;
import com.surexu.sesame.data.modelFieldExt.StringModelField;
import com.surexu.sesame.data.modelFieldExt.TextModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.data.task.TaskAttemptPolicy;
import com.surexu.sesame.data.task.TaskAttemptPolicy.Outcome;
import com.surexu.sesame.data.task.TaskAward;
import com.surexu.sesame.entity.AlipayAntForestHuntTaskList;
import com.surexu.sesame.entity.AlipayAntForestVitalityTaskList;
import com.surexu.sesame.entity.AlipayMonopolyTaskList;
import com.surexu.sesame.entity.AlipayUser;
import com.surexu.sesame.entity.CollectEnergyEntity;
import com.surexu.sesame.entity.CustomOption;
import com.surexu.sesame.entity.FriendWatch;
import com.surexu.sesame.entity.KVNode;
import com.surexu.sesame.entity.RpcEntity;
import com.surexu.sesame.entity.VitalityBenefit;
import com.surexu.sesame.entity.AlipayForestHunt;
import com.surexu.sesame.hook.ApplicationHook;
import com.surexu.sesame.hook.Toast;
import com.surexu.sesame.model.base.TaskCommon;
import com.surexu.sesame.model.base.TaskAlternative;
import com.surexu.sesame.model.extensions.ExtensionsHandle;
import com.surexu.sesame.model.normal.base.BaseModel;
import com.surexu.sesame.model.task.antFarm.AntFarm.TaskStatus;
import com.surexu.sesame.model.task.antFarm.AntFarmRpcCall;
import com.surexu.sesame.model.task.antGame.GameTask;
import com.surexu.sesame.rpc.intervallimit.FixedOrRangeIntervalLimit;
import com.surexu.sesame.rpc.intervallimit.RpcIntervalLimit;
import com.surexu.sesame.ui.ObjReference;
import com.surexu.sesame.util.AverageMath;
import com.surexu.sesame.util.FileUtil;
import com.surexu.sesame.util.JsonUtil;
import com.surexu.sesame.util.ListUtil;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.MessageUtil;
import com.surexu.sesame.util.NotificationUtil;
import com.surexu.sesame.util.RandomUtil;
import com.surexu.sesame.util.Statistics;
import com.surexu.sesame.util.Status;
import com.surexu.sesame.util.StringUtil;
import com.surexu.sesame.util.TimeUtil;
import com.surexu.sesame.util.idMap.AntForestHuntTaskListMap;
import com.surexu.sesame.util.idMap.AntForestVitalityTaskListMap;
import com.surexu.sesame.util.idMap.MonopolyTaskListMap;
import com.surexu.sesame.util.idMap.UserIdMap;
import com.surexu.sesame.util.idMap.VitalityBenefitIdMap;
import lombok.Getter;

/**
 * 蚂蚁森林V2
 */
public class AntForestV2 extends ModelTask implements EnergyCollectCallback {

    private static final String TAG = AntForestV2.class.getSimpleName();

    /**
     * 组队合种浇水本地标记：表示"本方法把用户切到了组队模式，还没切回来"。
     * 用于切回失败 / 进程被杀之后的校正，避免用户被永久留在组队模式。
     */
    private static final String FLAG_TEAM_MODE_SWITCHED = "Forest::teamWaterSwitchedToTeam";

    /**
     * 新版保护地任务场景码：跨保护地区域固定的场景标识。
     * 保护地区域会轮换（洪山动物园→玉树东仲→…），但 regionCode 由 entryInfo 动态下发、
     * sceneCode 始终复用此值；服务端按 regionCode 返回该区域任务，故区域轮换无需改代码。
     */
    private static final String MONOPOLY_TASK_SCENE_HSDWY = "ANTFOREST_MONOPOLY_TASK_HSDWY";

    /**
     * 新版保护地任务的浏览时长上限（秒）：服务端下发 timeCount 后要等这么久才能提交完成，
     * 超出上限视为异常数据，不阻塞本轮任务。
     */
    private static final long MONOPOLY_MAX_TASK_SECONDS = 300L;

    private static final AverageMath offsetTimeMath = new AverageMath(5);

    private static final Map<String, Long> usingProps = new ConcurrentHashMap<>();

    private static final Map<String, String> dressMap;

    private static final Set<String> AntForestTaskTypeSet;

    static {
        dressMap = new HashMap<>();
        // position To positionType
        dressMap.put("tree__main", "treeMain");
        dressMap.put("bg__sky_0", "bgSky0");
        dressMap.put("bg__sky_cloud", "bgSkyCloud");
        dressMap.put("bg__ground_a", "bgGroundA");
        dressMap.put("bg__ground_b", "bgGroundB");
        dressMap.put("bg__ground_c", "bgGroundC");
        // positionType To position
        dressMap.put("treeMain", "tree__main");
        dressMap.put("bgSky0", "bg__sky_0");
        dressMap.put("bgSkyCloud", "bg__sky_cloud");
        dressMap.put("bgGroundA", "bg__ground_a");
        dressMap.put("bgGroundB", "bg__ground_b");
        dressMap.put("bgGroundC", "bg__ground_c");

        AntForestTaskTypeSet = new HashSet<>();
        AntForestTaskTypeSet.add("VITALITYQIANDAOPUSH"); //
        AntForestTaskTypeSet.add("ONE_CLICK_WATERING_V1"); // 给随机好友一键浇水
        AntForestTaskTypeSet.add("GYG_YUEDU_2"); // 去森林图书馆逛15s
        AntForestTaskTypeSet.add("GYG_TBRS"); // 逛一逛淘宝人生
        AntForestTaskTypeSet.add("TAOBAO_tab2_2023"); // 去淘宝看科普视频
        AntForestTaskTypeSet.add("GYG_diantao"); // 逛一逛点淘得红包
        AntForestTaskTypeSet.add("GYG-taote"); // 逛一逛淘宝特价版
        AntForestTaskTypeSet.add("NONGCHANG_20230818"); // 逛一逛淘宝芭芭农场
        // AntForestTaskTypeSet.add("GYG_haoyangmao_20240103");//逛一逛淘宝薅羊毛
        // AntForestTaskTypeSet.add("YAOYIYAO_0815");//去淘宝摇一摇领奖励
        // AntForestTaskTypeSet.add("GYG-TAOCAICAI");//逛一逛淘宝买菜
    }

    private final AtomicInteger taskCount = new AtomicInteger(0);

    private String selfId;

    private Integer tryCountInt;

    private Integer retryIntervalInt;

    private Integer advanceTimeInt;

    private Integer checkIntervalInt;

    private FixedOrRangeIntervalLimit collectIntervalEntity;

    private FixedOrRangeIntervalLimit doubleCollectIntervalEntity;

    private final AverageMath delayTimeMath = new AverageMath(5);

    private final ObjReference<Long> collectEnergyLockLimit = new ObjReference<>(0L);

    private final Object usePropLockObj = new Object();

    private BooleanModelField collectEnergy;
    private BooleanModelField expiredEnergy;
    private BooleanModelField energyRain;
    private IntegerModelField advanceTime;
    private IntegerModelField tryCount;
    private IntegerModelField retryInterval;
    private SelectModelField dontCollectList;

    private BooleanModelField drawGameCenterAward;
    private ChoiceModelField CollectSelfEnergyType;

    private IntegerModelField CollectSelfEnergyThreshold;
    private IntegerModelField collectRobExpandEnergy;
    private BooleanModelField collectRobExpandEnergyEnable;
    private BooleanModelField collectWateringBubble;
    private BooleanModelField batchRobEnergy;
    private BooleanModelField balanceNetworkDelay;
    //PK能量
    private BooleanModelField pkEnergy;
    private ChoiceModelField whackModeName;
    private IntegerModelField whackModeGames;
    private IntegerModelField whackModeCount;
    private IntegerModelField earliestwhackMoleTime;

    // 定义运行模式名称数组（需提前声明，与原 Kotlin 中的 whackMoleModeNames 对应）

    private BooleanModelField collectProp;
    private StringModelField queryInterval;
    private StringModelField collectInterval;
    private StringModelField doubleCollectInterval;
    private ChoiceModelField doubleClickType;
    private ListModelField.ListJoinCommaToStringModelField doubleCardTime;
    @Getter
    private IntegerModelField doubleCountLimit;
    private IntegerModelField CollectBombEnergyLimit;
    private BooleanModelField useEnergyRainLimit;
    private BooleanModelField doubleCardConstant;
    private ChoiceModelField helpFriendCollectType;
    private SelectModelField helpFriendCollectList;

    private IntegerModelField helpFriendCollectListLimit;
    private BooleanModelField returnWater;
    private IntegerModelField returnWater33;
    private IntegerModelField returnWater18;
    private IntegerModelField returnWater10;
    private BooleanModelField receiveForestTaskAward;
    private BooleanModelField energySceneTask;

    private BooleanModelField AutoAntForestVitalityTaskList;
    private SelectModelField AntForestVitalityTaskList;
    private ChoiceModelField waterFriendType;
    private BooleanModelField waterFriendEnergySendChat;

    private BooleanModelField waterFriendEnergyFirst;
    private SelectAndCountModelField waterFriendList;

    private SelectAndCountModelField wateredFriendList;

    private BooleanModelField doubleWaterFriendEnergy;
    private SelectModelField giveEnergyRainList;
    private BooleanModelField vitalityExchangeBenefit;
    private SelectAndCountModelField vitality_ExchangeBenefitList;
    private BooleanModelField userPatrol;
    private BooleanModelField monopolyPatrol;
    private BooleanModelField monopolyTasks;
    private BooleanModelField monopolyAnimalEnergy;
    private BooleanModelField monopolyDispatchAnimal;
    private ChoiceModelField monopolyDispatchPriority;
    private BooleanModelField AutoMonopolyTaskList;
    private SelectModelField MonopolyTaskList;
    private BooleanModelField collectGiftBox;
    private BooleanModelField medicalHealth;
    private BooleanModelField greenLife;
    private BooleanModelField marketBrowseTask;

    private BooleanModelField greenRent;
    private ChoiceModelField consumeAnimalPropType;
    private SelectModelField whoYouWantToGiveTo;
    private BooleanModelField ecoLife;
    private BooleanModelField youthPrivilege;
    private SelectModelField ecoLifeOptions;
    private BooleanModelField dress;
    private BooleanModelField readForest;
    private TextModelField dressDetailList;

    private static int totalCollected = 0;
    private static int totalHelpCollected = 0;
    private static boolean hasErrorWait = false;

    @Getter
    private Set<String> dontCollectMap = new HashSet<>();

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "森林";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }

    private BooleanModelField loveteamWater;
    private IntegerModelField loveteamWaterNum;

    private BooleanModelField partnerteamWater;
    private IntegerModelField partnerteamWaterNum;
    private BooleanModelField ForestHunt;
    private BooleanModelField AutoAntForestHuntTaskList;
    private SelectModelField AntForestHuntTaskList;
    private BooleanModelField ForestHuntDraw;
    private BooleanModelField ForestHuntHelp;
    private SelectModelField ForestHuntHelpList;

    private SelectModelField continuousUseCardOptions;

    private BooleanModelField autoUseShieldCard;
    private IntegerModelField continuousUseShieldHour;
    private BooleanModelField NORMALForestHuntHelp;
    private BooleanModelField ACTIVITYForestHuntHelp;

    private BooleanModelField energyPvp;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(collectEnergy = new BooleanModelField("collectEnergy", "收集能量",true));
        modelFields.addField(dontCollectList = new SelectModelField("dontCollectList", "不收取能量列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(batchRobEnergy = new BooleanModelField("batchRobEnergy", "一键收取",true));
        modelFields.addField(CollectSelfEnergyType = new ChoiceModelField("CollectSelfEnergyType", "收单个能量球 | " + "方式", CollectSelfType.ALL, CollectSelfType.nickNames));
        modelFields.addField(CollectSelfEnergyThreshold = new IntegerModelField("CollectSelfEnergyThreshold", "收单个能量球阈值(0不限制)", 0, 0, 10000));
        modelFields.addField(pkEnergy = new BooleanModelField("pkEnergy", "Pk榜收取 | 开关",true));
        modelFields.addField(energyPvp = new BooleanModelField("energyPvp", "1V1能量挑战 | 开关",true));
        modelFields.addField(collectWateringBubble = new BooleanModelField("collectWateringBubble", "收取金球",true));
        modelFields.addField(wateredFriendList = new SelectAndCountModelField("wateredFriendList", "统计 | 应被好友浇水", new LinkedHashMap<>(), AlipayUser::getList, "请填写被浇水次数(用于核对金球)", 1, 3));
        modelFields.addField(collectRobExpandEnergyEnable = new BooleanModelField("collectRobExpandEnergyEnable", "倍卡额外能量", true));
        modelFields.addField(collectRobExpandEnergy = new IntegerModelField("collectRobExpandEnergy", "倍卡额外能量(大于该值收取)", 0, 0, 1000000).setDependsOn("collectRobExpandEnergyEnable"));
        modelFields.addField(expiredEnergy = new BooleanModelField("expiredEnergy", "收取过期能量",true));
        modelFields.addField(queryInterval = new StringModelField("queryInterval", "查询间隔(毫秒或毫秒范围)", "800-1500"));
        modelFields.addField(collectInterval = new StringModelField("collectInterval", "收取间隔" + "(毫秒或毫秒范围)", "1000" + "-1500"));
        modelFields.addField(doubleCollectInterval = new StringModelField("doubleCollectInterval", "双击间隔(毫秒或毫秒范围)", "50-150"));
        modelFields.addField(balanceNetworkDelay = new BooleanModelField("balanceNetworkDelay", "平衡网络延迟", true));
        modelFields.addField(advanceTime = new IntegerModelField("advanceTime", "提前时间(毫秒)", 0, 0, 500));
        modelFields.addField(tryCount = new IntegerModelField("tryCount", "尝试收取(次数)", 1, 0, 10));
        modelFields.addField(retryInterval = new IntegerModelField("retryInterval", "重试间隔(毫秒)", 1000, 0, 10000));
        modelFields.addField(drawGameCenterAward = new BooleanModelField("drawGameCenterAward", "森林乐园 | 游戏宝箱", true));
        modelFields.addField(CollectBombEnergyLimit = new IntegerModelField("CollectBombEnergyLimit", "单个炸弹能量大于该值收取", 0, 0, 100000));
        modelFields.addField(continuousUseCardOptions = new SelectModelField("continuousUseCardOptions", "连续兑换使用道具卡片 | 选项", new LinkedHashSet<>(), CustomOption::getContinuousUseCardOptions));
        modelFields.addField(autoUseShieldCard = new BooleanModelField("autoUseShieldCard", "自动续用保护罩",true));
        modelFields.addField(continuousUseShieldHour = new IntegerModelField("continuousUseShieldHour", "自动续用保护罩(小时)", 24, 1, 168).setDependsOn("autoUseShieldCard"));
        //modelFields.addField(doubleClickType = new ChoiceModelField("doubleClickType", "双击卡 | " + "自动使用", UsePropType.CLOSE, UsePropType.nickNames));
        //modelFields.addField(doubleCountLimit = new IntegerModelField("doubleCountLimit", "双击卡 | " + "使用次数", 6));
        //modelFields.addField(doubleCardTime = new ListModelField.ListJoinCommaToStringModelField("doubleCardTime", "双击卡 | 使用时间(范围)", ListUtil.newArrayList("0700" + "-0730")));
        //modelFields.addField(doubleCardConstant = new BooleanModelField("DoubleCardConstant", "双击卡 | 限时双击永动机",true));
        modelFields.addField(returnWater = new BooleanModelField("returnWater", "返水 | 开启",true));
        modelFields.addField(returnWater10 = new IntegerModelField("returnWater10", "返水 | 10克需收能量(0不限)", 10).setDependsOn("returnWater"));
        modelFields.addField(returnWater18 = new IntegerModelField("returnWater18", "返水 | 18克需收能量(0不限)", 18).setDependsOn("returnWater"));
        modelFields.addField(returnWater33 = new IntegerModelField("returnWater33", "返水 | 33克需收能量(0不限)", 33).setDependsOn("returnWater"));
        modelFields.addField(waterFriendType = new ChoiceModelField("waterFriendType", "浇水 | 动作", WaterFriendType.WATER_00, WaterFriendType.nickNames));
        modelFields.addField(waterFriendList = new SelectAndCountModelField("waterFriendList", "浇水 | 好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写浇水次数(每日)", 1, 3));
        modelFields.addField(waterFriendEnergySendChat = new BooleanModelField("waterFriendEnergySendChat", "浇水 | 发送已浇水提醒", false));
        modelFields.addField(waterFriendEnergyFirst = new BooleanModelField("waterFriendEnergyFirst", "浇水 | 每次首先执行",true));
        modelFields.addField(doubleWaterFriendEnergy = new BooleanModelField("doubleWaterFriendEnergy", "浇水 | 强制检查重复一次",true));
        modelFields.addField(helpFriendCollectType = new ChoiceModelField("helpFriendCollectType", "复活能量 | 动作", HelpFriendCollectType.NONE, HelpFriendCollectType.nickNames));
        modelFields.addField(helpFriendCollectList = new SelectModelField("helpFriendCollectList", "复活能量 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(helpFriendCollectListLimit = new IntegerModelField("helpFriendCollectListLimit", "复活好友能量下限(大于该值复活,0不限制)", 0, 0, 100000).setDependsOn("helpFriendCollectType"));
        modelFields.addField(vitalityExchangeBenefit = new BooleanModelField("vitalityExchangeBenefit", "活力值 | 兑换权益",true));
        modelFields.addField(vitality_ExchangeBenefitList = new SelectAndCountModelField("vitality_ExchangeBenefitList", "活力值 | 权益列表", new LinkedHashMap<>(), VitalityBenefit::getList, "请填写兑换次数(每日)").setDependsOn("vitalityExchangeBenefit"));
        modelFields.addField(whackModeName = new ChoiceModelField("whackModeName", "6秒拼手速 | 运行模式", whackModeNames.CLOSE, whackModeNames.nickNames));
        modelFields.addField(whackModeGames = new IntegerModelField("whackModeGames", "6秒拼手速 | 激进模式局数", 5).setDependsOn("whackModeName"));
        modelFields.addField(whackModeCount = new IntegerModelField("whackModeCount", "6秒拼手速 | 兼容模式击打数", 15).setDependsOn("whackModeName"));
        modelFields.addField(earliestwhackMoleTime = new IntegerModelField("earliestwhackMoleTime", "6秒拼手速 | 最早执行(24小时制)", 8, 0, 23).setDependsOn("whackModeName"));
        modelFields.addField(collectProp = new BooleanModelField("collectProp", "收集道具",true));
        modelFields.addField(whoYouWantToGiveTo = new SelectModelField("whoYouWantToGiveTo", "赠送道具好友列表", new LinkedHashSet<>(), AlipayUser::getList, "会赠送所有可送道具都给已选择的好友"));
        modelFields.addField(energyRain = new BooleanModelField("energyRain", "收集能量雨",true));
        modelFields.addField(giveEnergyRainList = new SelectModelField("giveEnergyRainList", "赠送能量雨好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(useEnergyRainLimit = new BooleanModelField("useEnergyRainLimit", "兑换使用限时能量雨卡",true));
        modelFields.addField(userPatrol = new BooleanModelField("userPatrol", "旧版保护地巡护",true));
        modelFields.addField(monopolyPatrol = new BooleanModelField("monopolyPatrol", "新版保护地 | 自动前进",true));
        modelFields.addField(monopolyTasks = new BooleanModelField("monopolyTasks", "新版保护地 | 自动任务",true));
        modelFields.addField(monopolyAnimalEnergy = new BooleanModelField("monopolyAnimalEnergy", "新版动物伙伴 | 领取能量",true));
        modelFields.addField(monopolyDispatchAnimal = new BooleanModelField("monopolyDispatchAnimal", "新版动物伙伴 | 自动派遣",true));
        modelFields.addField(monopolyDispatchPriority = new ChoiceModelField("monopolyDispatchPriority", "新版动物伙伴 | 派遣优先级", 0, new String[]{"新版优先", "旧版优先"}).setDependsOn("monopolyDispatchAnimal"));
        modelFields.addField(AutoMonopolyTaskList = new BooleanModelField("AutoMonopolyTaskList", "新版保护地 | 自动黑名单", true).setDependsOn("monopolyTasks"));
        modelFields.addField(MonopolyTaskList = new SelectModelField("MonopolyTaskList", "新版保护地 | 黑名单列表", new LinkedHashSet<>(), AlipayMonopolyTaskList::getList).setDependsOn("AutoMonopolyTaskList"));
        modelFields.addField(consumeAnimalPropType = new ChoiceModelField("consumeAnimalPropType", "派遣动物伙伴", ConsumeAnimalPropType.NONE, ConsumeAnimalPropType.nickNames));
        modelFields.addField(receiveForestTaskAward = new BooleanModelField("receiveForestTaskAward", "森林任务",true));
        modelFields.addField(energySceneTask = new BooleanModelField("energySceneTask", "种树攻略 | 场景任务",true)
                .setDependsOn("receiveForestTaskAward")
                .setDescription("逐个尝试完成场景卡下的行为子任务"));
        modelFields.addField(AutoAntForestVitalityTaskList = new BooleanModelField("AutoAntForestVitalityTaskList", "活力值 | 自动黑名单", true));
        modelFields.addField(AntForestVitalityTaskList = new SelectModelField("AntForestVitalityTaskList", "活力值 | 黑名单列表", new LinkedHashSet<>(), AlipayAntForestVitalityTaskList::getList).setDependsOn("AutoAntForestVitalityTaskList"));
        modelFields.addField(collectGiftBox = new BooleanModelField("collectGiftBox", "领取礼盒",true));
        modelFields.addField(medicalHealth = new BooleanModelField("medicalHealth", "医疗健康",true));
        modelFields.addField(greenLife = new BooleanModelField("greenLife", "森林集市",true));
        modelFields.addField(marketBrowseTask = new BooleanModelField("marketBrowseTask", "绿植浏览",true));
        modelFields.addField(greenRent = new BooleanModelField("greenRent", "绿色租赁",true));
        modelFields.addField(youthPrivilege = new BooleanModelField("youthPrivilege", "青春特权 | 森林道具",true));
        modelFields.addField(ecoLife = new BooleanModelField("ecoLife", "绿色行动 | 开启",true));
        modelFields.addField(ecoLifeOptions = new SelectModelField("ecoLifeOptions", "绿色行动 | 选项", new LinkedHashSet<>(), CustomOption::getEcoLifeOptions, "光盘行动需要先手动完成一次").setDependsOn("ecoLife"));
        modelFields.addField(partnerteamWater = new BooleanModelField("partnerteamWater", "组队合种浇水", false));
        modelFields.addField(partnerteamWaterNum = new IntegerModelField("partnerteamWaterNum", "组队合种浇水" + "(g)", 10, 10, 5000).setDependsOn("partnerteamWater"));
        modelFields.addField(loveteamWater = new BooleanModelField("loveteamWater", "真爱合种浇水", false));
        modelFields.addField(loveteamWaterNum = new IntegerModelField("loveteamWaterNum", "真爱合种浇水" + "(g)", 20, 20, 10000).setDependsOn("loveteamWater"));
        modelFields.addField(ForestHunt = new BooleanModelField("ForestHunt", "森林寻宝",true));
        modelFields.addField(AutoAntForestHuntTaskList = new BooleanModelField("AutoAntForestHuntTaskList", "抽抽乐任务 | 自动黑名单", true).setDependsOn("ForestHunt"));
        modelFields.addField(AntForestHuntTaskList = new SelectModelField("AntForestHuntTaskList", "抽抽乐任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntForestHuntTaskList::getList).setDependsOn("AutoAntForestHuntTaskList"));
        modelFields.addField(ForestHuntDraw = new BooleanModelField("ForestHuntDraw", "森林寻宝抽奖",true).setDependsOn("ForestHunt"));
        modelFields.addField(ForestHuntHelp = new BooleanModelField("ForestHuntHelp", "森林寻宝助力",true).setDependsOn("ForestHunt"));
        modelFields.addField(NORMALForestHuntHelp = new BooleanModelField("NORMALForestHuntHelp", "普通场景强制助力" + "(助力任务不在列表中时使用，如果日志显示失效请关闭)",true).setDependsOn("ForestHunt"));
        modelFields.addField(ACTIVITYForestHuntHelp = new BooleanModelField("ACTIVITYForestHuntHelp", "活动场景强制助力" + "(同上)",true).setDependsOn("ForestHunt"));
        modelFields.addField(ForestHuntHelpList = new SelectModelField("ForestHuntHelpList", "点击配置寻宝助力列表" + "(填写shareId中开头的22-24位字符在\"4O7FEYDgn\"前的)", new LinkedHashSet<>(), AlipayForestHunt::getList).setDependsOn("ForestHunt"));
        modelFields.addField(readForest = new BooleanModelField("readForest", "无纸阅读",true));
        modelFields.addField(dress = new BooleanModelField("dress", "装扮保护 | 开启",true));
        modelFields.addField(dressDetailList = new TextModelField("dressDetailList", "装扮保护 | " + "装扮信息", "").setDependsOn("dress"));
        modelFields.addField(new EmptyModelField("dressDetailListClear", "装扮保护 | 装扮信息清除", () -> dressDetailList.reset()).setDependsOn("dress"));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime) > System.currentTimeMillis()) {
            Log.record("异常等待中，暂不执行检测！");
            return false;
        }
        return true;
    }

    @Override
    public Boolean isSync() {
        return true;
    }

    @Override
    public void boot(ClassLoader classLoader) {
        super.boot(classLoader);
        FixedOrRangeIntervalLimit queryIntervalLimit = new FixedOrRangeIntervalLimit(queryInterval.getValue(), 10, 10000);
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryHomePage", queryIntervalLimit);
        // 首页与好友家各持一个实例：限流时间戳存在实例里（见 RpcIntervalLimit.enterIntervalLimit），
        // 共用同一实例会让两个接口互相刷新时间戳、串行等待，收取整体变慢。
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryFriendHomePage",
                new FixedOrRangeIntervalLimit(queryInterval.getValue(), 10, 10000));
        RpcIntervalLimit.addIntervalLimit("alipay.antmember.forest.h5.collectEnergy", 0);
        RpcIntervalLimit.addIntervalLimit("alipay.antmember.forest.h5.queryEnergyRanking", 100);
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.fillUserRobFlag", 500);
        tryCountInt = tryCount.getValue();
        retryIntervalInt = retryInterval.getValue();
        advanceTimeInt = advanceTime.getValue();
        checkIntervalInt = BaseModel.getCheckInterval().getValue();
        dontCollectMap = dontCollectList.getValue();
        collectIntervalEntity = new FixedOrRangeIntervalLimit(collectInterval.getValue(), 50, 10000);
        doubleCollectIntervalEntity = new FixedOrRangeIntervalLimit(doubleCollectInterval.getValue(), 10, 5000);
        delayTimeMath.clear();
        AntForestRpcCall.init();
    }

    @Override
    public void run() {
        try {
            NotificationUtil.setStatusTextExec(getName());
            taskCount.set(0);
            selfId = UserIdMap.getCurrentUid();
            hasErrorWait = false;
            EnergyWaitingManager.getInstance().setEnergyCollectCallback(this);
            EnergyWaitingManager.getInstance().init(task -> true);

            // 组队合种浇水异常中断后，把账号从组队模式恢复回个人模式
            fixTeamModeIfNeeded();

            if (waterFriendEnergyFirst.getValue()) {
                waterFriendEnergy();
            }


            if (useEnergyRainLimit.getValue()) {
                useEnergyRainCard();
            }

            if (energyRain.getValue()) {
                energyRain();
            }

            if (ecoLife.getValue()) {
                ecoLife();
            }

            if (youthPrivilege.getValue()) {
                Privilege.youthPrivilege();
                //Privilege.studentSignInRedEnvelope();
            }
            //连续兑换使用道具卡片
            continuousUseCardOptions();

            if (receiveForestTaskAward.getValue()) {
                vantiepSign();
            }

            JSONObject selfHomeObject = collectSelfEnergy();
            try {
                JSONObject friendsObject = new JSONObject(AntForestRpcCall.queryEnergyRanking());
                if (MessageUtil.checkResultCode(TAG, friendsObject)) {
                    collectFriendsEnergy(friendsObject, "ordinary");
                    int pos = 20;
                    List<String> idList = new ArrayList<>();
                    JSONArray totalDatas = friendsObject.getJSONArray("totalDatas");
                    while (pos < totalDatas.length()) {
                        JSONObject friend = totalDatas.getJSONObject(pos);
                        idList.add(friend.getString("userId"));
                        pos++;
                        if (pos % 20 == 0) {
                            collectFriendsEnergy(idList, "ordinary");
                            idList.clear();
                        }
                    }
                    if (!idList.isEmpty()) {
                        collectFriendsEnergy(idList, "ordinary");
                    }
                }
                selfHomeObject = collectSelfEnergy();
            } catch (Throwable t) {
                Log.err(TAG, "queryEnergyRanking err:", t);
            }

            if (!TaskCommon.IS_ENERGY_TIME && selfHomeObject != null) {
                String whackMoleStatus = selfHomeObject.optString("whackMoleStatus");
                if (Objects.equals("CAN_PLAY", whackMoleStatus) || Objects.equals("CAN_INITIATIVE_PLAY", whackMoleStatus) || Objects.equals("NEED_MORE_FRIENDS", whackMoleStatus)) {
                    checkAndHandleWhackMole();
                }
                boolean hasMore = false;
                do {
                    if (hasMore) {
                        hasMore = false;
                        // 重拉主页失败(限流/网络异常)时 querySelfHome 返回 null，直接赋值会让后续 selfHomeObject 解引用抛 NPE；
                        // 这里失败即保留上一份有效数据，行为等价于"本轮不再翻页"
                        JSONObject refreshedHomeObject = querySelfHome();
                        if (refreshedHomeObject != null) {
                            selfHomeObject = refreshedHomeObject;
                        }
                    }
                    if (collectWateringBubble.getValue()) {
                        JSONArray wateringBubbles = selfHomeObject.has("wateringBubbles") ? selfHomeObject.getJSONArray("wateringBubbles") : new JSONArray();
                        if (wateringBubbles.length() > 0) {
                            int collected = 0;
                            for (int i = 0; i < wateringBubbles.length(); i++) {
                                JSONObject wateringBubble = wateringBubbles.getJSONObject(i);
                                String bizType = wateringBubble.getString("bizType");
                                String friendShowName = UserIdMap.getShowName(wateringBubble.getString("userId"));
                                switch (bizType) {
                                    case "jiaoshui": {
                                        JSONObject joEnergy = new JSONObject(AntForestRpcCall.collectEnergy(bizType, selfId, wateringBubble.getLong("id")));
                                        if (MessageUtil.checkResultCode("收取[我]的浇水金球", joEnergy)) {
                                            JSONArray bubbles = joEnergy.getJSONArray("bubbles");
                                            for (int j = 0; j < bubbles.length(); j++) {
                                                collected = bubbles.getJSONObject(j).getInt("collectedEnergy");
                                            }

                                            if (collected > 0) {
                                                //记录被浇水次数
                                                Status.wateredFriendToday(wateringBubble.getString("userId"));
                                                Statistics.addData(Statistics.DataType.WATEREDCOUNT, 1);
                                                String msg = "收取金球🍯[" + friendShowName + "]的浇水[" + collected + "g]";
                                                Log.forest(msg + "");
                                                Toast.show(msg);
                                                totalCollected += collected;
                                                Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                            } else {
                                                Log.record("收取[我]的浇水金球失败");
                                            }
                                        }
                                        break;
                                    }
                                    case "fuhuo": {
                                        JSONObject joEnergy = new JSONObject(AntForestRpcCall.collectRebornEnergy());
                                        if (MessageUtil.checkResultCode("收取[我]的复活金球", joEnergy)) {
                                            collected = joEnergy.getInt("energy");
                                            String msg = "收取金球🍯复活[" + collected + "g]";
                                            Log.forest(msg + "");
                                            Toast.show(msg);
                                            totalCollected += collected;
                                            Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                        }
                                        break;
                                    }
                                    case "baohuhuizeng": {
                                        JSONObject joEnergy = new JSONObject(AntForestRpcCall.collectEnergy(bizType, selfId, wateringBubble.getLong("id")));
                                        if (MessageUtil.checkResultCodeString("收取[" + friendShowName + "]的复活回赠金球", joEnergy)) {
                                            JSONArray bubbles = joEnergy.getJSONArray("bubbles");
                                            for (int j = 0; j < bubbles.length(); j++) {
                                                collected = bubbles.getJSONObject(j).getInt("collectedEnergy");
                                            }
                                            if (collected > 0) {
                                                String msg = "收取金球🍯[" + friendShowName + "]复活回赠[" + collected + "g]";
                                                Log.forest(msg + "");
                                                Toast.show(msg);
                                                totalCollected += collected;
                                                Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                            } else {
                                                Log.record("收取[" + friendShowName + "]的复活回赠金球失败");
                                            }
                                        }
                                        break;
                                    }
                                }
                                TimeUtil.sleep(1000L);
                            }
                            if (wateringBubbles.length() >= 20) {
                                hasMore = true;
                            }
                        }
                    }
                    if (collectProp.getValue()) {
                        JSONArray givenProps = selfHomeObject.has("givenProps") ? selfHomeObject.getJSONArray("givenProps") : new JSONArray();
                        if (givenProps.length() > 0) {
                            for (int i = 0; i < givenProps.length(); i++) {
                                JSONObject jo = givenProps.getJSONObject(i);
                                String giveConfigId = jo.getString("giveConfigId");
                                String giveId = jo.getString("giveId");
                                String propName = jo.getJSONObject("propConfig").getString("propName");
                                jo = new JSONObject(AntForestRpcCall.collectProp(giveConfigId, giveId));
                                if (MessageUtil.checkSuccess(TAG, jo)) {
                                    Log.forest("领取道具🎭[" + propName + "]");
                                }
                                TimeUtil.sleep(1000L);
                            }
                            if (givenProps.length() >= 20) {
                                hasMore = true;
                            }
                        }
                    }
                } while (hasMore);
                //JSONArray usingUserProps = selfHomeObject.has("usingUserProps") ? selfHomeObject.getJSONArray("usingUserProps") : new JSONArray();
                //JSONArray usingUserProps = selfHomeObject.has("usingUserPropsNew") ? selfHomeObject.getJSONArray("usingUserPropsNew") : new JSONArray();
                JSONArray usingUserProps;
                if (selfHomeObject.has("usingUserPropsNew")) {
                    usingUserProps = selfHomeObject.getJSONArray("usingUserPropsNew");
                } else {
                    usingUserProps = selfHomeObject.has("usingUserProps") ? selfHomeObject.getJSONArray("usingUserProps") : new JSONArray();
                }
                boolean canConsumeAnimalProp = true;
                if (usingUserProps.length() > 0) {
                    for (int i = 0; i < usingUserProps.length(); i++) {
                        JSONObject jo = usingUserProps.getJSONObject(i);
                        if (!Objects.equals("animal", jo.optString("propGroup"))) {
                            continue;
                        } else {
                            canConsumeAnimalProp = false;
                        }
                        JSONObject extInfo = new JSONObject(jo.getString("extInfo"));
                        int energy = extInfo.optInt("energy", 0);
                        if (energy > 0 && !extInfo.optBoolean("isCollected")) {
                            String propId = jo.getString("propId");
                            String propType = jo.getString("propType");
                            String shortDay = extInfo.getString("shortDay");
                            String animalName = extInfo.getJSONObject("animal").getString("name");
                            jo = new JSONObject(AntForestRpcCall.collectAnimalRobEnergy(propId, propType, shortDay));
                            if (MessageUtil.checkResultCode(TAG, jo)) {
                                Log.forest("动物能量🦩派遣" + animalName + "收取能量[" + energy + "g]");
                            }
                            TimeUtil.sleep(500);
                            break;
                        }
                    }
                }
                //强制重复浇水一次
                if (doubleWaterFriendEnergy.getValue()) {
                    if (!Status.hasFlagToday("Forest::doubleWaterFriendEnergy")) {
                        doubleWaterFriendEnergy();
                    }
                }

                waterFriendEnergy();

                if (pkEnergy.getValue()) {
                    collectPKEnergy();
                }

                //初始任务列表
                if (!Status.hasFlagToday("BlackList::initAntForest")) {
                    initAntForestTaskListMap(AutoAntForestVitalityTaskList.getValue(), AutoAntForestHuntTaskList.getValue(), receiveForestTaskAward.getValue(), ForestHunt.getValue());
                    Status.flagToday("BlackList::initAntForest");
                }
                // 新版保护地任务名单来自服务端下发：本地为空时每轮补一次，开了开关不必等到次日
                if (monopolyTasks.getValue() && MonopolyTaskListMap.getMap().isEmpty()) {
                    initMonopolyTaskListMap();
                }

                // 组队合种浇水
                if (partnerteamWater.getValue()) {
                    teamCooperateWater();
                }
                // 真爱合种浇水
                if (loveteamWater.getValue()) {
                    if (loveteamWaterNum.getValue() >= 20 && loveteamWaterNum.getValue() <= 10000) {
                        loveteam(loveteamWaterNum.getValue());
                    }
                }

                // 森林寻宝
                if (ForestHunt.getValue()) {
                    ForestChouChouLe forestChouChouLe = new ForestChouChouLe();
                    forestChouChouLe.chouChouLe(ForestHuntDraw.getValue(), ForestHuntHelp.getValue(), ForestHuntHelpList.getValue(), NORMALForestHuntHelp.getValue(), ACTIVITYForestHuntHelp.getValue(), AntForestHuntTaskList.getValue());
                }

                if (userPatrol.getValue()) {
                    queryUserPatrol();
                }
                if (monopolyPatrol.getValue()) {
                    monopolyPatrol();
                }
                if (monopolyAnimalEnergy.getValue()) {
                    collectMonopolyAnimalEnergy();
                }
                // 动物伙伴自动派遣：新版受独立开关与优先级控制，旧版仍只看"派遣动物伙伴"选择
                Integer monopolyDispatchPriorityValue = monopolyDispatchPriority.getValue();
                boolean newAnimalFirst = monopolyDispatchPriorityValue == null || monopolyDispatchPriorityValue == 0;
                boolean animalDispatched = false;
                if (monopolyDispatchAnimal.getValue() && newAnimalFirst) {
                    animalDispatched = dispatchMonopolyAnimal(canConsumeAnimalProp);
                }
                if (consumeAnimalPropType.getValue() != ConsumeAnimalPropType.NONE) {
                    if (!canConsumeAnimalProp) {
                        Log.record("已经有动物伙伴在巡护森林");
                    } else if (!animalDispatched) {
                        animalDispatched = queryAnimalPropList();
                    }
                }
                if (monopolyDispatchAnimal.getValue() && !animalDispatched && !newAnimalFirst) {
                    dispatchMonopolyAnimal(canConsumeAnimalProp);
                }
                if (expiredEnergy.getValue()) {
                    popupTask();
                }

                if (receiveForestTaskAward.getValue()) {
                    queryCommonSign();
                    queryTaskList();
                }

                giveProp();
                if (vitalityExchangeBenefit.getValue()) {
                    vitalityExchangeBenefit();
                }
                /* 森林集市 */
                if (greenLife.getValue()) {
                    greenLife();
                }

                // 绿植浏览（独立任务，与森林集市无关）
                if (marketBrowseTask.getValue()) {
                    GreenPlantBrowse.run();
                }

                // 绿色租赁
                if (greenRent.getValue()) {
                    if (!Status.hasFlagToday("Forest::greenRent")) {
                        greenRent();
                        Status.flagToday("Forest::greenRent");
                    }
                }

                if (medicalHealth.getValue()) {
                    // 医疗健康 绿色医疗 16g*6能量
                    queryForestEnergy("FEEDS");
                    // 医疗健康 电子小票 4g*10能量
                    queryForestEnergy("BILL");
                }
                // 无纸阅读: 读免费书攒绿色能量
                if (readForest.getValue()) {
                    ReadForestTask.execute();
                }
                if (dress.getValue()) {
                    dress();
                }

                checkAndHandleWhackMole();

                //森林乐园
                if (drawGameCenterAward.getValue()) {
                    doforestgame();
                    queryOptionalPlay();
                }

                //1V1能量挑战
                if (energyPvp.getValue()) {
                    if (updateUserConfigEnergyPvp()) {
                        queryPvpHomeInfo();
                        receivePvpRewards();
                    }

                }
                ForestEnergyInfo();

                // 本轮所有收取动作已做完：用服务端明细校准今日收取能量（见 syncTodayCollectedFromServer）
                syncTodayCollectedFromServer();
            }
        } catch (Throwable t) {
            Log.err(TAG, "AntForestV2.run err:", t);
        } finally {
            try {
                synchronized (AntForestV2.this) {
                    int count = taskCount.get();
                    if (count > 0) {
                        AntForestV2.this.wait(TimeUnit.MINUTES.toMillis(30));
                        count = taskCount.get();
                    }
                    if (count > 0) {
                        Log.record("执行超时-蚂蚁森林");
                    } else if (count != 0) {
                        Log.record("执行完成-蚂蚁森林");
                    }
                }
            } catch (InterruptedException ie) {
                Log.i(TAG, "执行中断-蚂蚁森林");
            }
            // 倍卡额外能量放到最后收：此时本轮的能量（含异步收取的好友能量）才累积完，
            // 放在收取之前只能收到上一轮攒下的部分
            collectRobExpandEnergy();
            Statistics.save();
            FriendWatch.save();
        }
    }

    /** 每页条数：200 条/页在今天量级下 1~2 页即可拉完，兼顾请求数与翻页次数。 */
    private static final int ENERGY_RECORD_PAGE_SIZE = 200;

    /** 明细翻页上限：防服务端异常恒回 hasMore 时死循环。 */
    private static final int ENERGY_RECORD_MAX_PAGES = 20;

    /**
     * 用服务端能量明细**覆盖**今日收取能量，取代逐点累加的口径。
     * <p>只统计**今日 0 点之后**的记录：{@code nextId} 是时间戳游标，从 0 开始倒序翻页，
     * 一旦遇到早于今日起点的记录即可停止（更早的必定更旧）。
     * <p>只计**进账**（energy>0）：明细里混有合种浇水等支出记录（energy 为负），
     * 按净额求和会让今日/本月/今年统计与支付宝展示对不上。
     * <p>拉取失败或解析不到记录时保留原值——覆盖是"定值"，不该把已有统计冲成 0。
     */
    private void syncTodayCollectedFromServer() {
        try {
            Calendar todayStart = Calendar.getInstance();
            todayStart.set(Calendar.HOUR_OF_DAY, 0);
            todayStart.set(Calendar.MINUTE, 0);
            todayStart.set(Calendar.SECOND, 0);
            todayStart.set(Calendar.MILLISECOND, 0);
            long todayStartMillis = todayStart.getTimeInMillis();

            long nextId = 0;
            int todayTotal = 0;
            int scanned = 0;
            boolean reachedOlder = false;
            for (int page = 0; page < ENERGY_RECORD_MAX_PAGES; page++) {
                JSONObject jo = new JSONObject(AntForestRpcCall.pagingQueryEnergyRecord(nextId, ENERGY_RECORD_PAGE_SIZE));
                // 响应层级随调用通道而异：内置抓包里明细挂在 resData 下，而模块 requestString
                // 拿到的是解析后的业务体（recordVOList 直接平铺在顶层）。两处都取，避免漏解析。
                JSONObject resData = jo.optJSONObject("resData");
                if (resData == null) {
                    resData = jo;
                }
                JSONArray records = resData.optJSONArray("recordVOList");
                if (records == null) {
                    // 拉取失败：宁可不更新（保留旧值），也不要把今日统计冲成 0
                    Log.record("能量明细⚠️无明细字段[" + TaskAlternative.describe(jo) + "]#今日统计保持原值");
                    return;
                }
                if (records == null || records.length() == 0) {
                    break;
                }
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.optJSONObject(i);
                    if (record == null) {
                        continue;
                    }
                    long bizDate = record.optLong("bizDate", 0);
                    if (bizDate < todayStartMillis) {
                        // 已翻过今日边界：更早的记录不必再看
                        reachedOlder = true;
                        break;
                    }
                    scanned++;
                    // 服务端明细同时含进账（收取能量）与支出（如合种浇水、能量捐赠），支出记录 energy 为负。
                    // 只计进账（energy>0）才能与支付宝展示口径一致——按净额求和会把支出也扣进去，
                    // 导致今日/本月/今年统计全部偏小、与支付宝对不上。
                    int energy = record.optInt("energy", 0);
                    if (energy > 0) {
                        todayTotal += energy;
                    }
                }
                if (reachedOlder || !resData.optBoolean("hasMore", false)) {
                    break;
                }
                long next = resData.optLong("nextId", 0);
                if (next <= 0 || next == nextId) {
                    // 游标未推进：再翻会重复计数，直接收手
                    break;
                }
                nextId = next;
                TimeUtil.sleep(300);
            }

            // 一条都没扫到（接口改版/字段变动）时不能覆盖：否则会把已有统计冲成 0
            if (scanned == 0) {
                Log.record("能量明细⚠️未解析到任何记录#今日统计保持原值");
                return;
            }
            int delta = Statistics.setDayCollectedFromServer(todayTotal);
            if (delta != 0) {
                Statistics.save();
                Log.forest("今日能量⚡️[按服务端明细校准]#今日" + todayTotal + "g"
                        + (delta > 0 ? "#补记" + delta : "#修正" + delta) + "g");
            }
        } catch (Throwable t) {
            Log.err(TAG, "syncTodayCollectedFromServer err:", t);
        }
    }

    private void ForestEnergyInfo() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray bubbles = jo.getJSONArray("bubbles");
            int bubblesNumber = bubbles.length();

            if (!jo.has("userBaseInfo")) {
                return;
            }
            JSONObject userBaseInfo = jo.getJSONObject("userBaseInfo");
            int currentEnergy = userBaseInfo.optInt("currentEnergy", 0);
            int totalCertCount = userBaseInfo.optInt("totalCertCount", 0);
            if (!jo.has("userVitalityInfo")) {
                return;
            }
            JSONObject userVitalityInfo = jo.getJSONObject("userVitalityInfo");
            int totalVitalityAmount = userVitalityInfo.optInt("totalVitalityAmount", 0);

            jo = new JSONObject(AntForestRpcCall.queryDynamicsIndex());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            if (!jo.has("todayEnergySummary")) {
                return;
            }
            JSONObject todayEnergySummary = jo.getJSONObject("todayEnergySummary");
            int obtainTotal = todayEnergySummary.optInt("obtainTotal", 0);
            int robbedTotal = todayEnergySummary.optInt("robbedTotal", 0);

            //获取能量日榜top
            jo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "day"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("myself")) {
                return;
            }
            JSONObject myself = jo.getJSONObject("myself");
            int dayenergySummation = myself.optInt("energySummation", 0);
            int dayrank = myself.optInt("rank", 0);

            //获取能量周榜top
            jo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "week"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("myself")) {
                return;
            }
            myself = jo.getJSONObject("myself");
            int weekenergySummation = myself.optInt("energySummation", 0);
            int weekrank = myself.optInt("rank", 0);

            //获取能量总榜top
            jo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "total"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("myself")) {
                return;
            }
            myself = jo.getJSONObject("myself");
            int totalenergySummation = myself.optInt("energySummation", 0);
            int totalrank = myself.optInt("rank", 0);

            //获取偷我日榜top
            String dayenergySummationtop3 = "偷我日榜top3:";
            String userId;
            int energySummation;
            jo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("robRank", "day"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("friendRanking")) {
                return;
            }
            JSONArray friendRankings = jo.getJSONArray("friendRanking");
            for (int i = 0; i < (Math.max(friendRankings.length(), 3)); i++) {
                JSONObject friendRanking = friendRankings.getJSONObject(i);
                energySummation = friendRanking.optInt("energySummation", 0);
                if (energySummation == 0) {
                    break;
                }
                userId = friendRanking.optString("userId", null);
                dayenergySummationtop3 = dayenergySummationtop3 + "[" + UserIdMap.getShowName(userId) + "]" + energySummation + "g;";
            }

            //获取偷我周榜top
            String weekenergySummationtop3 = "偷我周榜top3:";
            jo = new JSONObject(AntForestRpcCall.queryTopEnergyRanking("robRank", "week"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("friendRanking")) {
                return;
            }
            friendRankings = jo.getJSONArray("friendRanking");
            for (int i = 0; i < (Math.max(friendRankings.length(), 3)); i++) {
                JSONObject friendRanking = friendRankings.getJSONObject(i);
                energySummation = friendRanking.optInt("energySummation", 0);
                if (energySummation == 0) {
                    break;
                }
                userId = friendRanking.optString("userId", null);
                weekenergySummationtop3 = weekenergySummationtop3 + "[" + UserIdMap.getShowName(userId) + "]" + energySummation + "g;";
            }
            String ForestInfo = "森林榜单🌳收取" + obtainTotal + "g;被收" + robbedTotal + "g;能量球" + bubblesNumber + "个;活力值" + totalVitalityAmount + ";当前能量" + currentEnergy + "g;证书" + totalCertCount + ";😡" + dayenergySummationtop3 + weekenergySummationtop3 + "😁日榜第" + dayrank + "名:" + dayenergySummation + "g;周榜第" + weekrank + "名:" + weekenergySummation + "g;总榜第" + totalrank + "名:" + totalenergySummation + "g;";
            Log.record(ForestInfo);

        } catch (Throwable th) {
            Log.err(TAG, "ForestEnergyInfo err:", th);
        }

    }

    private void collectPKEnergy() {
        try {
            JSONObject pkObject = new JSONObject(AntForestRpcCall.queryTopEnergyChallengeRanking());
            if (!MessageUtil.checkResultCode(TAG + "获取PK排行榜失败:", pkObject)) {
                Log.error("获取PK排行榜失败: " + pkObject.optString("resultDesc"));
            } else {
                if (!pkObject.getString("rankMemberStatus").equals("JOIN")) {
                    Log.record("未加入PK排行榜");
                    return;
                }
                collectFriendsEnergy(pkObject, "PK");
                //继续处理靠后的PK好友
                JSONArray totalData = pkObject.optJSONArray("totalData");
                if (totalData == null || totalData.length() == 0) {
                    Log.record("PK好友排行榜为空，跳过");
                    return;
                }
                List<String> pkIdList = new ArrayList<>();
                for (int pos = 20; pos < totalData.length(); pos++) {
                    JSONObject pkFriend = totalData.getJSONObject(pos);
                    String userId = pkFriend.getString("userId");
                    if (Objects.equals(userId, selfId)) {
                        continue; //如果是自己则跳过
                    }
                    pkIdList.add(userId);
                    if (pkIdList.size() == 20) {
                        collectFriendsEnergy(pkIdList, "PK");
                        pkIdList.clear();
                    }
                }
                if (!pkIdList.isEmpty()) {
                    collectFriendsEnergy(pkIdList, "PK");
                }
                Log.record("收取PK能量🧾完成！");
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
    }

    private void notifyMain() {
        if (taskCount.decrementAndGet() < 1) {
            synchronized (AntForestV2.this) {
                AntForestV2.this.notifyAll();
            }
        }
    }

    private JSONObject querySelfHome() {
        JSONObject userHomeObject = null;
        try {
            long start = System.currentTimeMillis();
            userHomeObject = new JSONObject(AntForestRpcCall.queryHomePage());
            long end = System.currentTimeMillis();
            long serverTime = userHomeObject.getLong("now");
            int offsetTime = offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));
            // 降为 debug：每条好友查询都打一条, 遍历好友时反复刷屏;
            // 时间差实际只有几百毫秒(网络往返), 对蹲点调度无影响, 需要核对时再开调试日志
            Log.debug("服务器时间：" + serverTime + "，本地与服务器时间差：" + offsetTime);
            //兼容组队模式
            if (isTeam(userHomeObject)) {
                JSONObject teamHomeResult = userHomeObject.optJSONObject("teamHomeResult");
                JSONObject mainMember = teamHomeResult != null ? teamHomeResult.optJSONObject("mainMember") : null;
                //取出组队模式下的selfHomeObject
                if (mainMember != null) {
                    Iterator<String> keyIterator = mainMember.keys();
                    while (keyIterator.hasNext()) {
                        String key = keyIterator.next();
                        Object value = mainMember.get(key);
                        //将道具卡详情存为一般森林主页格式，以便统一解析
                        if (key.equals("usingUserProps")) {
                            key = "usingUserPropsNew";
                        }
                        // 核心方法：put()
                        // 效果：存在该 key 则覆盖原值，不存在则新增键值对
                        userHomeObject.put(key, value);
                    }
                }
                //userHomeObject = teamHomeResult != null ? teamHomeResult.optJSONObject("mainMember") : null;
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
            // 失败响应体(缺少 now 等业务字段)会让对象成为"半成品"：置空，避免调用方判空通过后按正常主页解析
            userHomeObject = null;
        }
        return userHomeObject;
    }

    private JSONObject queryFriendHome(String userId) {
        JSONObject userHomeObject = null;
        try {
            long start = System.currentTimeMillis();
            userHomeObject = new JSONObject(AntForestRpcCall.queryFriendHomePage(userId));
            long end = System.currentTimeMillis();
            long serverTime = userHomeObject.getLong("now");
            int offsetTime = offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));
            // 降为 debug：每条好友查询都打一条, 遍历好友时反复刷屏;
            // 时间差实际只有几百毫秒(网络往返), 对蹲点调度无影响, 需要核对时再开调试日志
            Log.debug("服务器时间：" + serverTime + "，本地与服务器时间差：" + offsetTime);
        } catch (Throwable t) {
            Log.printStackTrace(t);
            // 同上：好友主页失败响应置空，调用方(queryFriendHome 4 处)均已判空
            userHomeObject = null;
        }
        return userHomeObject;
    }

    private JSONObject collectSelfEnergy() {
        try {
            JSONObject selfHomeObject = querySelfHome();
            if (selfHomeObject != null) {
                if (whackModeName.getValue() == whackModeNames.CLOSE) {
                    JSONObject propertiesObject = selfHomeObject.optJSONObject("properties");
                    if (propertiesObject != null) {
                        if (Objects.equals("Y", propertiesObject.optString("whackMole"))) {
                            if (com.surexu.sesame.model.task.antForest.WhackMole.closeWhackMole()) {
                                Log.record("6秒拼手速🧾关闭成功");
                            } else {
                                Log.record("6秒拼手速⚠️关闭失败");
                            }
                        }
                    }
                }
                String nextAction = selfHomeObject.optString("nextAction");
                if ("WhackMole".equalsIgnoreCase(nextAction)) {
                    Log.record("检测到6秒拼手速强制弹窗，先执行拼手速");
                    checkAndHandleWhackMole();
                }
                return collectUserEnergy(UserIdMap.getCurrentUid(), selfHomeObject, "ordinary");
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return null;
    }

    private JSONObject collectFriendEnergy(String userId, String getType) {
        if (hasErrorWait) {
            return null;
        }
        try {
            JSONObject userHomeObject = queryFriendHome(userId);
            if (userHomeObject != null) {
                return collectUserEnergy(userId, userHomeObject, getType);
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return null;
    }

    private JSONObject collectUserEnergy(String userId, JSONObject userHomeObject, String getType) {
        try {
            if (!MessageUtil.checkResultCode(TAG, userHomeObject)) {
                return userHomeObject;
            }

            long serverTime = userHomeObject.getLong("now");
            boolean isSelf = Objects.equals(userId, selfId);
            String userName;
            boolean isCollectEnergy;
            //默认收炸弹能量
            boolean isBombCollectenergy = true;
            if (getType.equals("PK")) {
                JSONObject userBaseInfo = userHomeObject.getJSONObject("userBaseInfo");
                userName = userBaseInfo.optString("displayName") + "(PK森友)";
                isCollectEnergy = true;
            } else {
                userName = UserIdMap.getMaskName(userId);
                isCollectEnergy = collectEnergy.getValue() && !dontCollectMap.contains(userId);
            }

            if (isSelf) {
                Log.record("进入[" + userName + "]的蚂蚁森林");
                updateUsingPropsEndTime(userHomeObject);
            } else {
                if (isCollectEnergy) {
                    JSONArray jaProps = userHomeObject.getJSONArray("usingUserPropsNew");
                    for (int i = 0; i < jaProps.length(); i++) {
                        JSONObject joProp = jaProps.getJSONObject(i);
                        if (Objects.equals("shield", joProp.getString("propGroup"))) {
                            if (joProp.getLong("endTime") > serverTime) {
                                Log.record("[" + userName + "]能量罩保护到[" + TimeUtil.getCommonDateS(joProp.getLong("endTime")) + "]");
                                isCollectEnergy = false;
                                JSONArray jaBubbles = userHomeObject.getJSONArray("bubbles");
                                for (int ii = 0; ii < jaBubbles.length(); ii++) {
                                    JSONObject canbubble = jaBubbles.getJSONObject(ii);
                                    long bubbleId = canbubble.getLong("id");
                                    switch (CollectStatus.valueOf(canbubble.getString("collectStatus"))) {
                                        case AVAILABLE:
                                            break;
                                        case WAITING:
                                            long produceTime = canbubble.getLong("produceTime");
                                            //如果保护罩不能覆盖能量成熟时间
                                            if (produceTime < joProp.getLong("endTime")) {
                                                break;
                                            }
                                            if (checkIntervalInt + checkIntervalInt / 2 > produceTime - serverTime) {
                                                if (EnergyWaitingManager.getInstance().hasWaitingTask(userId, bubbleId, produceTime)) {
                                                    break;
                                                }
                                                EnergyWaitingManager.getInstance().addWaitingTask(userId, userName, bubbleId, produceTime, "保护过期抢收", joProp.getLong("endTime"), 0, userHomeObject, false, 0, false);
                                                Log.record("[" + userName + "]能量保护罩时间[" + TimeUtil.getCommonDate(joProp.getLong("endTime")) + "]#未覆盖能量球成熟时间[" + TimeUtil.getCommonDate(produceTime) + "]");
                                                Log.record("添加蹲点收取[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                            } else {
                                                Log.i("用户[" + userName + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
                                            }
                                            break;
                                    }
                                }
                                break;
                            }
                        }
                        if (Objects.equals("energyBombCard", joProp.getString("propGroup"))) {
                            if (joProp.getLong("endTime") > serverTime) {
                                Log.record("[" + userName + "]使用了炸弹卡");
                                if (userHomeObject.has("bubbles")) {
                                    JSONArray jaBubbles = userHomeObject.getJSONArray("bubbles");
                                    for (int ii = 0; ii < jaBubbles.length(); ii++) {
                                        JSONObject Bombubble = jaBubbles.getJSONObject(ii);
                                        int remainEnergy = Bombubble.optInt("remainEnergy");
                                        //存在小于预设值
                                        if (remainEnergy < CollectBombEnergyLimit.getValue()) {
                                            isBombCollectenergy = false;
                                        } else {
                                            Log.record("[" + userName + "]炸弹能量[" + remainEnergy + "g]>设定值[" + CollectBombEnergyLimit.getValue() + "g]");
                                            isBombCollectenergy = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isBombCollectenergy) {
                isCollectEnergy = false;
            }

            if (isCollectEnergy) {
                JSONArray jaBubbles = userHomeObject.getJSONArray("bubbles");
                List<Long> bubbleIdList = new ArrayList<>();
                for (int i = 0; i < jaBubbles.length(); i++) {
                    JSONObject bubble = jaBubbles.getJSONObject(i);
                    int remainEnergy = bubble.optInt("remainEnergy");
                    long bubbleId = bubble.getLong("id");
                    switch (CollectStatus.valueOf(bubble.getString("collectStatus"))) {
                        case AVAILABLE:
                            //用阈值判断单个能量球需收取情况
                            if (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) {
                                bubbleIdList.add(bubbleId);
                            } else if ((CollectSelfEnergyType.getValue() == CollectSelfType.OVER_THRESHOLD) && (remainEnergy >= CollectSelfEnergyThreshold.getValue())) {
                                bubbleIdList.add(bubbleId);
                            } else if (((CollectSelfEnergyType.getValue() == CollectSelfType.BELOW_THRESHOLD) && (remainEnergy <= CollectSelfEnergyThreshold.getValue()))) {
                                bubbleIdList.add(bubbleId);
                            }
                            break;
                        case WAITING:
                            long produceTime = bubble.getLong("produceTime");
                            if (checkIntervalInt + checkIntervalInt / 2 > produceTime - serverTime) {
                                if (EnergyWaitingManager.getInstance().hasWaitingTask(userId, bubbleId, produceTime)) {
                                    break;
                                }
                                if (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) {
                                    EnergyWaitingManager.getInstance().addWaitingTask(userId, userName, bubbleId, produceTime, "waiting", 0, 0, userHomeObject, false, 0, false);
                                    Log.record("添加蹲点收取[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                } else if ((CollectSelfEnergyType.getValue() == CollectSelfType.OVER_THRESHOLD) && (remainEnergy >= CollectSelfEnergyThreshold.getValue())) {
                                    EnergyWaitingManager.getInstance().addWaitingTask(userId, userName, bubbleId, produceTime, "waiting", 0, 0, userHomeObject, false, 0, false);
                                    Log.record("添加蹲点收取[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                } else if (((CollectSelfEnergyType.getValue() == CollectSelfType.BELOW_THRESHOLD) && (remainEnergy <= CollectSelfEnergyThreshold.getValue()))) {
                                    EnergyWaitingManager.getInstance().addWaitingTask(userId, userName, bubbleId, produceTime, "waiting", 0, 0, userHomeObject, false, 0, false);
                                    Log.record("添加蹲点收取[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                }
                            } else {
                                Log.i("用户[" + userName + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
                            }
                            break;
                    }
                }
                //兼容组队模式
                JSONObject selfHomeObject = new JSONObject(AntForestRpcCall.queryHomePage());
                //不是自己或者是自己不在组队模式全收的情况
                //if (batchRobEnergy.getValue() && (!isSelf || (CollectSelfEnergyType.getValue() == CollectSelfType.ALL && !isTeam(selfHomeObject)))) {
                //不在组队模式全收的情况
                if (batchRobEnergy.getValue() && (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) && teamState(selfHomeObject) == 0) {
                    Iterator<Long> iterator = bubbleIdList.iterator();
                    List<Long> batchBubbleIdList = new ArrayList<>();
                    while (iterator.hasNext()) {
                        batchBubbleIdList.add(iterator.next());
                        if (batchBubbleIdList.size() >= 6) {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)), userName);
                            batchBubbleIdList = new ArrayList<>();
                        }
                    }
                    int size = batchBubbleIdList.size();
                    if (size > 0) {
                        if (size == 1) {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, batchBubbleIdList.get(0))), userName);
                        } else {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)), userName);
                        }
                    }
                } else {
                    for (Long bubbleId : bubbleIdList) {
                        collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), userName);
                    }
                }
            }

            return userHomeObject;
        } catch (Throwable t) {
            Log.err(TAG, "collectUserEnergy err:", t);
        }
        return null;
    }

    private void collectFriendsEnergy(List<String> idList, String getType) {
        try {
            if (hasErrorWait) {
                return;
            }
            collectFriendsEnergy(new JSONObject(AntForestRpcCall.fillUserRobFlag(new JSONArray(idList).toString())), getType);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void collectFriendsEnergy(JSONObject friendsObject, String getType) {
        if (hasErrorWait) {
            return;
        }
        try {
            JSONArray jaFriendRanking = friendsObject.optJSONArray("friendRanking");
            if (jaFriendRanking == null) {
                return;
            }
            for (int i = 0, len = jaFriendRanking.length(); i < len; i++) {
                try {
                    JSONObject friendObject = jaFriendRanking.getJSONObject(i);
                    String userId = friendObject.getString("userId");
                    if (Objects.equals(userId, selfId)) {
                        continue;
                    }
                    JSONObject userHomeObject = null;
                    if (getType.equals("PK")) {
                        boolean collectEnergy = true;
                        if (!friendObject.optBoolean("canCollectEnergy")) {
                            long canCollectLaterTime = friendObject.getLong("canCollectLaterTime");
                            if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > checkIntervalInt)) {
                                collectEnergy = false;
                            }
                        }
                        if (collectEnergy) {
                            userHomeObject = collectFriendEnergy(userId, getType);
                        }
                    } else {
                        if (collectEnergy.getValue() && !dontCollectMap.contains(userId)) {
                            boolean collectEnergy = true;
                            if (!friendObject.optBoolean("canCollectEnergy")) {
                                long canCollectLaterTime = friendObject.getLong("canCollectLaterTime");
                                if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > checkIntervalInt)) {
                                    collectEnergy = false;
                                }
                            }
                            if (collectEnergy) {
                                userHomeObject = collectFriendEnergy(userId, getType);
                            } /* else {
                  Log.i("不收取[" + UserIdMap.getNameById(userId) + "], userId=" + userId);
              }*/
                        }

                        if (helpFriendCollectType.getValue() != HelpFriendCollectType.NONE && friendObject.optBoolean("canProtectBubble") && !Status.hasFlagToday("forest::protectBubble")) {
                            boolean isHelpCollect = helpFriendCollectList.getValue().contains(userId);
                            if (helpFriendCollectType.getValue() != HelpFriendCollectType.HELP) {
                                isHelpCollect = !isHelpCollect;
                            }
                            if (isHelpCollect) {
                                if (userHomeObject == null) {
                                    userHomeObject = queryFriendHome(userId);
                                }
                                if (userHomeObject != null) {
                                    protectFriendEnergy(userHomeObject);
                                }
                            }
                        }
                        if (collectGiftBox.getValue() && friendObject.getBoolean("canCollectGiftBox")) {
                            if (userHomeObject == null) {
                                userHomeObject = queryFriendHome(userId);
                            }
                            if (userHomeObject != null) {
                                collectGiftBox(userHomeObject);
                            }
                        }
                    }
                } catch (Exception t) {
                    Log.err(TAG, "collectFriendEnergy err:", t);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void collectGiftBox(JSONObject userHomeObject) {
        try {
            JSONObject giftBoxInfo = userHomeObject.optJSONObject("giftBoxInfo");
            JSONObject userEnergy = userHomeObject.optJSONObject("userEnergy");
            String userId = userEnergy == null ? UserIdMap.getCurrentUid() : userEnergy.optString("userId");
            if (giftBoxInfo != null) {
                JSONArray giftBoxList = giftBoxInfo.optJSONArray("giftBoxList");
                if (giftBoxList != null && giftBoxList.length() > 0) {
                    for (int ii = 0; ii < giftBoxList.length(); ii++) {
                        try {
                            JSONObject giftBox = giftBoxList.getJSONObject(ii);
                            String giftBoxId = giftBox.getString("giftBoxId");
                            String title = giftBox.getString("title");
                            JSONObject giftBoxResult = new JSONObject(AntForestRpcCall.collectFriendGiftBox(giftBoxId, userId));
                            if (!MessageUtil.checkResultCode(TAG, giftBoxResult)) {
                                continue;
                            }
                            int energy = giftBoxResult.optInt("energy", 0);
                            Log.forest("礼盒能量🎁[" + UserIdMap.getMaskName(userId) + "-" + title + "]#" + energy + "g");
                            Statistics.addData(Statistics.DataType.COLLECTED, energy);
                        } catch (Throwable t) {
                            Log.printStackTrace(t);
                            break;
                        } finally {
                            TimeUtil.sleep(500);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void protectFriendEnergy(JSONObject userHomeObject) {
        try {
            JSONArray wateringBubbles = userHomeObject.optJSONArray("wateringBubbles");
            JSONObject userEnergy = userHomeObject.optJSONObject("userEnergy");
            String userId = userEnergy == null ? UserIdMap.getCurrentUid() : userEnergy.optString("userId");
            if (wateringBubbles != null && wateringBubbles.length() > 0) {
                for (int j = 0; j < wateringBubbles.length(); j++) {
                    try {
                        JSONObject wateringBubble = wateringBubbles.getJSONObject(j);
                        if (!Objects.equals("fuhuo", wateringBubble.getString("bizType"))) {
                            continue;
                        }
                        if (wateringBubble.getJSONObject("extInfo").optInt("restTimes", 0) == 0) {
                            Status.flagToday("forest::protectBubble");
                        }
                        if (!wateringBubble.getBoolean("canProtect")) {
                            continue;
                        }
                        int fullEnergy = wateringBubble.optInt("fullEnergy", 0);
                        if (fullEnergy < helpFriendCollectListLimit.getValue()) {
                            continue;
                        }
                        JSONObject joProtect = new JSONObject(AntForestRpcCall.protectBubble(userId));
                        if (!MessageUtil.checkResultCode(TAG, joProtect)) {
                            continue;
                        }
                        int vitalityAmount = joProtect.optInt("vitalityAmount", 0);

                        String str = "复活能量🧾[" + UserIdMap.getMaskName(userId) + "-" + fullEnergy + "g]" + (vitalityAmount > 0 ? "#活力值+" + vitalityAmount : "");
                        Log.forest(str);
                        totalHelpCollected += fullEnergy;
                        Statistics.addData(Statistics.DataType.HELPED, fullEnergy);

                        break;
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                        break;
                    } finally {
                        TimeUtil.sleep(500);
                    }
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void collectEnergy(CollectEnergyEntity collectEnergyEntity, String username) {
        collectEnergy(collectEnergyEntity, false, username);
    }

    private void collectEnergy(CollectEnergyEntity collectEnergyEntity, Boolean joinThread, String username) {
        if (hasErrorWait) {
            return;
        }
        Runnable runnable = () -> {
            try {
                String userId = collectEnergyEntity.getUserId();
                RpcEntity rpcEntity = collectEnergyEntity.getRpcEntity();
                boolean needDouble = collectEnergyEntity.getNeedDouble();
                boolean needRetry = collectEnergyEntity.getNeedRetry();
                int tryCount = collectEnergyEntity.addTryCount();
                int collected = 0;
                long startTime;
                synchronized (collectEnergyLockLimit) {
                    long sleep;
                    if (needDouble) {
                        collectEnergyEntity.unsetNeedDouble();
                        sleep = doubleCollectIntervalEntity.getInterval() - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    } else if (needRetry) {
                        collectEnergyEntity.unsetNeedRetry();
                        sleep = retryIntervalInt - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    } else {
                        sleep = collectIntervalEntity.getInterval() - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    }
                    if (sleep > 0) {
                        TimeUtil.sleep(sleep);
                    }
                    startTime = System.currentTimeMillis();
                    collectEnergyLockLimit.setForce(startTime);
                }
                ApplicationHook.requestObject(rpcEntity, 0, 0);
                long spendTime = System.currentTimeMillis() - startTime;
                if (balanceNetworkDelay.getValue()) {
                    delayTimeMath.nextInteger((int) (spendTime / 3));
                }
                if (rpcEntity.getHasError()) {
                    String errorCode = (String) XHelpers.callMethod(rpcEntity.getResponseObject(), "getString", "error");
                    if (Objects.equals("1004", errorCode)) {
                        if (BaseModel.getWaitWhenException().getValue() > 0) {
                            long waitTime = System.currentTimeMillis() + BaseModel.getWaitWhenException().getValue();
                            RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, waitTime);
                            NotificationUtil.updateStatusText("异常");
                            Log.record("触发异常,等待至" + TimeUtil.getCommonDate(waitTime));
                            hasErrorWait = true;
                            return;
                        }
                        TimeUtil.sleep(600 + RandomUtil.delay());
                    }
                    if (tryCount < tryCountInt) {
                        collectEnergyEntity.setNeedRetry();
                        collectEnergy(collectEnergyEntity, username);
                    }
                    return;
                }
                JSONObject jo = new JSONObject(rpcEntity.getResponseString());
                String resultCode = jo.getString("resultCode");
                if (!"SUCCESS".equalsIgnoreCase(resultCode)) {
                    if ("PARAM_ILLEGAL2".equals(resultCode)) {
                        Log.record("[" + username + "]" + "能量已被收取,取消重试 错误:" + jo.getString("resultDesc"));
                        return;
                    }
                    Log.record("[" + username + "]" + jo.getString("resultDesc"));
                    if (tryCount < tryCountInt) {
                        collectEnergyEntity.setNeedRetry();
                        collectEnergy(collectEnergyEntity, username);
                    }
                    return;
                }

                JSONArray jaBubbles = jo.getJSONArray("bubbles");

                int jaBubbleLength = jaBubbles.length();
                if (jaBubbleLength > 1) {
                    List<Long> newBubbleIdList = new ArrayList<>();
                    for (int i = 0; i < jaBubbleLength; i++) {
                        JSONObject bubble = jaBubbles.getJSONObject(i);
                        if (bubble.getBoolean("canBeRobbedAgain")) {
                            newBubbleIdList.add(bubble.getLong("id"));
                        }
                        collected += bubble.getInt("collectedEnergy");
                    }
                    if (collected > 0) {
                        FriendWatch.friendWatch(userId, collected);
                        String str;
                        if (jo.has("bombCardEffect")) {
                            JSONObject bombCardEffect = jo.getJSONObject("bombCardEffect");
                            int explodeEnergy = bombCardEffect.optInt("explodeEnergy", 0);
                            str = "一键收取🪂[" + username + "]#" + collected + "g被炸" + explodeEnergy + "g";
                        } else {
                            str = "一键收取🪂[" + username + "]#" + collected + "g";
                        }
                        if (needDouble) {
                            Log.forest(str + "耗时[" + spendTime + "]ms[双击]");
                            Toast.show(str + "[双击]");
                        } else {
                            Log.forest(str + "耗时[" + spendTime + "]ms");
                            Toast.show(str);
                        }
                        totalCollected += collected;
                        Statistics.addData(Statistics.DataType.COLLECTED, collected);
                    } else {
                        Log.record("一键收取[" + username + "]的能量失败，好友：" + userId + "，气泡：" + newBubbleIdList);
                    }
                    if (!newBubbleIdList.isEmpty()) {
                        collectEnergyEntity.setRpcEntity(AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, newBubbleIdList));
                        collectEnergyEntity.setNeedDouble();
                        collectEnergyEntity.resetTryCount();
                        collectEnergy(collectEnergyEntity, username);
                    }
                } else if (jaBubbleLength == 1) {
                    JSONObject bubble = jaBubbles.getJSONObject(0);
                    collected += bubble.getInt("collectedEnergy");
                    FriendWatch.friendWatch(userId, collected);
                    if (collected > 0) {
                        String str;
                        if (jo.has("bombCardEffect")) {
                            JSONObject bombCardEffect = jo.getJSONObject("bombCardEffect");
                            int explodeEnergy = bombCardEffect.optInt("explodeEnergy", 0);
                            str = "收取能量🪂[" + username + "]#" + collected + "g被炸" + explodeEnergy + "g";
                        } else {
                            str = "收取能量🪂[" + username + "]#" + collected + "g";
                        }

                        if (needDouble) {
                            Log.forest(str + "耗时[" + spendTime + "]ms[双击]");
                            Toast.show(str + "[双击]");
                        } else {
                            Log.forest(str + "耗时[" + spendTime + "]ms");
                            Toast.show(str);
                        }
                        totalCollected += collected;
                        Statistics.addData(Statistics.DataType.COLLECTED, collected);
                    } else {
                        Log.record("收取[" + username + "]的能量失败");
                        Log.i("，UserID：" + userId + "，BubbleId：" + bubble.getLong("id"));
                    }
                    if (bubble.getBoolean("canBeRobbedAgain")) {
                        collectEnergyEntity.setNeedDouble();
                        collectEnergyEntity.resetTryCount();
                        collectEnergy(collectEnergyEntity, username);
                        return;
                    }
                    JSONObject userHome = collectEnergyEntity.getUserHome();
                    if (userHome == null) {
                        return;
                    }
                    String bizNo = userHome.optString("bizNo");
                    if (bizNo.isEmpty()) {
                        return;
                    }
                    if (returnWater.getValue()) {
                        int returnCount = 0;
                        if (collected >= returnWater33.getValue()) {
                            returnCount = 33;
                        } else if (collected >= returnWater18.getValue()) {
                            returnCount = 18;
                        } else if (collected >= returnWater10.getValue()) {
                            returnCount = 10;
                        }
                        if (returnCount > 0) {
                            returnFriendWater(userId, bizNo, 1, returnCount);
                        }
                    }
                }
            } catch (Exception e) {
                Log.i("collectEnergy err:");
                Log.printStackTrace(e);
            } finally {
                Statistics.save();
                notifyMain();
            }
        };
        taskCount.incrementAndGet();
        if (joinThread) {
            runnable.run();
        } else {
            addChildTask(new ChildModelTask("CE|" + collectEnergyEntity.getUserId() + "|" + runnable.hashCode(), "CE", runnable));
        }
    }

    private void updateUsingPropsEndTime() throws JSONException {
        JSONObject joHomePage = new JSONObject(AntForestRpcCall.queryHomePage());
        TimeUtil.sleep(100);
        updateUsingPropsEndTime(joHomePage);
    }

    private void updateUsingPropsEndTime(JSONObject joHomePage) {
        try {
            JSONArray ja = joHomePage.getJSONArray("loginUserUsingPropNew");
            if (ja.length() == 0) {
                ja = joHomePage.getJSONArray("usingUserPropsNew");
            }
            for (int i = 0; i < ja.length(); i++) {
                JSONObject jo = ja.getJSONObject(i);
                usingProps.put(jo.getString("propGroup"), jo.getLong("endTime"));
            }
            forestExtensions();
        } catch (Throwable th) {
            Log.err(TAG, "updateUsingPropsEndTime err:", th);
        }
    }

    /**
     * 收取倍卡额外能量（本轮收尾调用）。倍卡额度是本轮收取行为累积出来的，
     * 先收只能收到上一轮攒下的部分，本轮领到的要等到下一轮；且好友能量是异步收取，
     * 必须等子任务跑完再查，否则查到的仍是未累积的旧值。
     */
    private void collectRobExpandEnergy() {
        if (!collectRobExpandEnergyEnable.getValue()) {
            return;
        }
        try {
            JSONObject joHomePage = new JSONObject(AntForestRpcCall.queryHomePage());
            if (MessageUtil.checkResultCode(TAG, joHomePage)) {
                collectRobExpandEnergy(joHomePage);
            }
        } catch (Throwable th) {
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    /** 从首页响应里取出在用的倍卡并收取 */
    private void collectRobExpandEnergy(JSONObject joHomePage) {
        try {
            JSONArray ja = joHomePage.getJSONArray("loginUserUsingPropNew");
            if (ja.length() == 0) {
                ja = joHomePage.getJSONArray("usingUserPropsNew");
            }
            for (int i = 0; i < ja.length(); i++) {
                JSONObject jo = ja.getJSONObject(i);
                if (PropGroup.robExpandCard.name().equals(jo.getString("propGroup"))) {
                    collectRobExpandEnergy(jo.optString("extInfo"), jo.getString("propId"), jo.getString("propType"));
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    private void collectRobExpandEnergy(String extInfo, String propId, String propType) {
        if (extInfo.isEmpty()) {
            return;
        }
        // 关闭"倍卡额外能量"开关：不收取额外能量
        if (!collectRobExpandEnergyEnable.getValue()) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(extInfo);
            double leftEnergy = Double.parseDouble(jo.optString("leftEnergy", "0"));
            // 开关开启：受数值限制，只有大于"倍卡额外能量"阈值才收取
            if (leftEnergy > collectRobExpandEnergy.getValue()) {
                collectRobExpandEnergy(propId, propType);
            }
        } catch (Throwable th) {
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    private void collectRobExpandEnergy(String propId, String propType) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.collectRobExpandEnergy(propId, propType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int collectEnergy = jo.optInt("collectEnergy");
                Log.forest("额外能量🎄收取[" + collectEnergy + "g]");
                totalCollected += collectEnergy;
                Statistics.addData(Statistics.DataType.COLLECTED, collectEnergy);
            }
        } catch (Throwable th) {
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    private void queryForestEnergy(String scene) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryForestEnergy(scene));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data").getJSONObject("response");
            JSONArray ja = jo.getJSONArray("energyGeneratedList");
            if (ja.length() > 0) {
                harvestForestEnergy(scene, ja);
            }
            int remainBubble = jo.optInt("remainBubble");
            for (int i = 0; i < remainBubble; i++) {
                ja = produceForestEnergy(scene);
                if (ja.length() == 0 || !harvestForestEnergy(scene, ja)) {
                    return;
                }
                TimeUtil.sleep(1000);
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryForestEnergy err:", th);
        }
    }

    private JSONArray produceForestEnergy(String scene) {
        JSONArray energyGeneratedList = new JSONArray();
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.produceForestEnergy(scene));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                jo = jo.getJSONObject("data").getJSONObject("response");
                energyGeneratedList = jo.getJSONArray("energyGeneratedList");
                if (energyGeneratedList.length() > 0) {
                    String title = scene.equals("FEEDS") ? "绿色医疗" : "电子小票";
                    int cumulativeEnergy = jo.getInt("cumulativeEnergy");
                    Log.forest("医疗健康🧾完成[" + title + "]#产生[" + cumulativeEnergy + "g能量]");
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "produceForestEnergy err:", th);
        }
        return energyGeneratedList;
    }

    private Boolean harvestForestEnergy(String scene, JSONArray bubbles) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.harvestForestEnergy(scene, bubbles));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            jo = jo.getJSONObject("data").getJSONObject("response");
            int collectedEnergy = jo.getInt("collectedEnergy");
            if (collectedEnergy > 0) {
                String title = scene.equals("FEEDS") ? "绿色医疗" : "电子小票";
                Log.forest("医疗健康🧾收取[" + title + "]#获得[" + collectedEnergy + "g能量]");
                totalCollected += collectedEnergy;
                Statistics.addData(Statistics.DataType.COLLECTED, collectedEnergy);
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "harvestForestEnergy err:", th);
        }
        return false;
    }

    private void checkAndHandleWhackMole() {
        try {
            // 获取当前选择的索引 (0, 1, 或 2)
            int modeIndex = (whackModeName != null) ? whackModeName.getValue() : 0;

            // 如果索引为 0 (关闭)，直接返回
            if (modeIndex == 0) {
                return;
            }

            // 检查执行时间
            int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);
            if (hour >= earliestwhackMoleTime.getValue()) {
                String whackMoleFlag = "forest::whackMole::executed";
                if (Status.hasFlagToday(whackMoleFlag)) {
                    return;
                }

                // 根据索引匹配模式
                switch (modeIndex) {
                    case 1: // 兼容模式
                        Log.record("触发任务🎮拼手速:兼容模式");
                        WhackMole.setTotalGames(1);
                        int defaultMoleCount = (whackModeCount != null) ? whackModeCount.getValue() : 15;
                        WhackMole.setMoleCount(defaultMoleCount);
                        WhackMole.start(WhackMole.Mode.COMPATIBLE);
                        break;

                    case 2: // 激进模式
                        Log.record("触发任务🎮拼手速:激进模式");
                        int configGames = (whackModeGames != null) ? whackModeGames.getValue() : 5;
                        WhackMole.setTotalGames(configGames);
                        WhackMole.start(WhackMole.Mode.AGGRESSIVE);
                        break;
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    public void initAntForestTaskListMap(boolean AutoAntForestVitalityTaskList, boolean AutoAntForestHuntTaskList, boolean receiveForestTaskAward, boolean ForestHunt) {
        try {

            //初始化AntForestVitalityTaskListMap
            AntForestVitalityTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            // 注："三国大冒险过1关征战"（小游戏）不再预置拉黑，交由自动拉黑机制判定；
            // 其余（邀请助力/添加组件/连续7天/到店支付/淘宝花花乐/健康问答）保留
            Set<String> blackList = new HashSet<>();
            blackList.add("邀请1位好友助力");
            blackList.add("添加组件及时收能量");
            blackList.add("到店支付得50g能量");
            blackList.add("践行绿色行为");
            blackList.add("连续7天收自己能量");
            blackList.add("去淘宝花花乐领红包");
            blackList.add("去蚂蚁阿福健康问答");

            // 可继续添加更多黑名单任务

            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("逛农场得落叶肥料");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntForestVitalityTaskListMap.add(task, task);
            }

            if (receiveForestTaskAward) {
                JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
                    if (forestTasksNew != null && forestTasksNew.length() != 0) {
                        for (int i = 0; i < forestTasksNew.length(); i++) {
                            JSONObject forestTask = forestTasksNew.getJSONObject(i);
                            JSONArray taskInfoList = forestTask.optJSONArray("taskInfoList");
                            if (taskInfoList != null && taskInfoList.length() != 0) {
                                for (int j = 0; j < taskInfoList.length(); j++) {
                                    JSONObject taskInfo = taskInfoList.getJSONObject(j);
                                    JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                                    JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                                    String taskType = taskBaseInfo.getString("taskType");
                                    String taskTitle = bizInfo.optString("taskTitle", taskType);
                                    AntForestVitalityTaskListMap.add(taskTitle, taskTitle);
                                }
                            }
                        }
                    }
                }

                jo = new JSONObject(AntForestRpcCall.listTaskopengreen());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    // 添加安全的空值判断
                    if (jo.has("taskInfoList")) {
                        JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                        if (taskInfoList != null && taskInfoList.length() != 0) {
                            for (int j = 0; j < taskInfoList.length(); j++) {
                                JSONObject taskInfo = taskInfoList.getJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                                String taskType = taskBaseInfo.getString("taskType");
                                String taskTitle = bizInfo.optString("taskTitle", taskType);
                                AntForestVitalityTaskListMap.add(taskTitle, taskTitle);
                            }
                        }
                    }
                }


                //保存任务到配置文件
                AntForestVitalityTaskListMap.save();
                Log.record("同步任务🉑森林活力值任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntForestVitalityTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                    SelectModelField AntForestVitalityTaskList = (SelectModelField) AntForestV2.get("AntForestVitalityTaskList");
                    if (AntForestVitalityTaskList == null) {
                        return;
                    }

                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("森林活力值任务", "AntForestVitalityTaskList", blackList, whiteList, AntForestVitalityTaskList);
                }
            }

            //初始化AntForestHuntTaskListMap
            AntForestHuntTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            // 注：抽抽乐里的游戏/开宝箱类不再预置拉黑，交由自动拉黑机制判定
            blackList = new HashSet<>();
            // 可继续添加更多黑名单任务

            whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("消耗活力值得机会");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntForestHuntTaskListMap.add(task, task);
            }

            if (ForestHunt) {
                JSONObject resData = new JSONObject(AntForestRpcCall.enterDrawActivityopengreen("", "ANTFOREST_NORMAL_DRAW", "task_entry"));
                if (MessageUtil.checkSuccess(TAG, resData)) {
                    JSONArray drawSceneGroups = resData.getJSONArray("drawSceneGroups");
                    for (int i = 0; i < drawSceneGroups.length(); i++) {
                        JSONObject drawScene = drawSceneGroups.getJSONObject(i);
                        JSONObject drawActivity = drawScene.getJSONObject("drawActivity");
                        String sceneCode = drawActivity.getString("sceneCode");
                        JSONObject listTaskopengreen = new JSONObject(AntForestRpcCall.listTaskopengreen(sceneCode + "_TASK", "task_entry"));
                        if (MessageUtil.checkSuccess(TAG, listTaskopengreen)) {
                            JSONArray taskList = listTaskopengreen.getJSONArray("taskInfoList");
                            for (int j = 0; j < taskList.length(); j++) {
                                JSONObject taskInfo = taskList.getJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                                String taskName = bizInfo.getString("title");
                                AntForestHuntTaskListMap.add(taskName, taskName);
                            }
                        }
                    }
                }
                AntForestHuntTaskListMap.save();
                Log.record("同步任务🉑森林抽抽乐任务列表");
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntForestHuntTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                    SelectModelField AntForestHuntTaskList = (SelectModelField) AntForestV2.get("AntForestHuntTaskList");
                    if (AntForestHuntTaskList == null) {
                        return;
                    }

                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("森林抽抽乐任务", "AntForestHuntTaskList", blackList, whiteList, AntForestHuntTaskList);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "initAntForestTaskListMap err:", t);
        }
    }

    /**
     * 新版保护地任务列表初始化：把服务端下发的任务名同步到 {@link MonopolyTaskListMap}，
     * 作为配置页「新版保护地 | 黑名单列表」的候选项。
     * <p>没有预置黑白名单：新版保护地任务名由服务端动态下发，全部交给自动拉黑机制判定。
     */
    public void initMonopolyTaskListMap() {
        try {
            MonopolyTaskListMap.load();
            // regionCode 由 entryInfo 动态下发(区域轮换无需改代码),只拉当前区域任务作为黑名单候选项
            String regionCode = getMonopolyRegionCode();
            if (!regionCode.isEmpty()) {
                JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(regionCode, MONOPOLY_TASK_SCENE_HSDWY));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                    if (taskInfoList != null) {
                        for (int i = 0; i < taskInfoList.length(); i++) {
                            JSONObject task = taskInfoList.optJSONObject(i);
                            JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                            if (base == null) {
                                continue;
                            }
                            String taskType = base.optString("taskType");
                            if (taskType.isEmpty()) {
                                continue;
                            }
                            String title = blackTaskKey(monopolyTaskTitle(base, taskType));
                            MonopolyTaskListMap.add(title, title);
                        }
                    }
                }
            }
            MonopolyTaskListMap.save();
            Log.record("同步任务🉑新版保护地任务列表");
        } catch (Throwable t) {
            Log.err(TAG, "initMonopolyTaskListMap err:", t);
        }
    }

    /* 森林集市 */
    private static void greenLife() {
        sendEnergyByAction("GREEN_LIFE");
        retrieveCurrentActivity();
    }

    // 绿色租赁
    private static void greenRent() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.checkUserSecondSceneChance());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            // 本轮不发能量时再请求生成会被服务端拒（SYSTEM_ERROR）：先看机会字段，直接跳过
            JSONObject chance = jo.optJSONObject("resultObject");
            if (chance != null && !chance.optBoolean("canSendEnergy", true)) {
                Log.i(TAG, "绿色租赁⏭️本轮无可发能量，跳过");
                return;
            }
            TimeUtil.sleep(200);
            jo = new JSONObject(AntForestRpcCall.generateEnergy());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }

            JSONObject resultObject = jo.getJSONObject("resultObject");
            JSONObject energyGenerated = resultObject.getJSONObject("energyGenerated");
            int zulinshangpinliulan = energyGenerated.getInt("zulinshangpinliulan");
            Log.forest("绿色租赁🛍️完成[线上逛街]#产生[" + zulinshangpinliulan + "g能量]");
            Toast.show("绿色租赁🛍️完成[线上逛街]#产生[" + zulinshangpinliulan + "g能量]");
            if (zulinshangpinliulan > 0) {
                totalCollected += zulinshangpinliulan;
                Statistics.addData(Statistics.DataType.COLLECTED, zulinshangpinliulan);
            }
        } catch (Throwable t) {
            Log.err(TAG, "greenRent err:", t);
        }
    }

    private static void retrieveCurrentActivity() {
        try {
            JSONObject jo = new JSONObject(GreenLifeRpcCall.retrieveCurrentActivity());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }

            jo = jo.getJSONObject("data");
            if (!jo.has("currentActivity")) {
                return;
            }
            JSONObject currentActivity = jo.getJSONObject("currentActivity");
            int numberOfDaysCompleted = currentActivity.getInt("numberOfDaysCompleted") + 1;
            JSONObject currentTask = jo.getJSONObject("currentTask");
            if (currentTask.getBoolean("checkInCompleted")) {
                return;
            }
            String taskTemplateId = currentTask.getString("taskTemplateId");
            jo = new JSONObject(GreenLifeRpcCall.finishCurrentTask(taskTemplateId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.getJSONObject("data");
            JSONArray ja = jo.getJSONArray("prizes");
            StringBuilder award = new StringBuilder();
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (i > 0) {
                    award.append(";");
                }
                award.append(jo.getString("name"));
            }
            if (award.length() > 0) {
                award = new StringBuilder("#获得[" + award + "]");
            }
            Log.forest("森林集市🛍️打卡[坚持" + numberOfDaysCompleted + "天]" + award);
        } catch (Throwable t) {
            Log.err(TAG, "retrieveCurrentActivity err:", t);
        }
    }

    private static void sendEnergyByAction(String sourceType) {
        try {
            // 先问询再发送：服务端不允许时（如当日额度用尽 USER_SEND_OVER_LIMIT）不发请求
            JSONObject jo = new JSONObject(GreenLifeRpcCall.consultForSendEnergyByAction(sourceType));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.optBoolean("canSendEnergy", false)) {
                Log.i(TAG, "森林集市⏭️[" + sourceType + "]不可发送能量["
                        + data.optString("resultCode", "") + "]，跳过");
                return;
            }
            jo = new JSONObject(GreenLifeRpcCall.sendEnergyByAction(sourceType));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            data = jo.getJSONObject("data");
            if (!data.optBoolean("canSendEnergy", false)) {
                // 受理但未发能量：记一行响应原文，供定位（额度正常时不会走到）
                Log.i(TAG, "森林集市⏭️[" + sourceType + "]未发能量#" + data);
                return;
            }
            int receivedEnergyAmount = data.getInt("receivedEnergyAmount");
            Log.forest("森林集市🛍️完成[线上逛街]#产生[" + receivedEnergyAmount + "g能量]");
            Toast.show("森林集市🛍️完成[线上逛街]#产生[" + receivedEnergyAmount + "g能量]");
            if (receivedEnergyAmount > 0) {
                totalCollected += receivedEnergyAmount;
                Statistics.addData(Statistics.DataType.COLLECTED, receivedEnergyAmount);
            }
        } catch (Throwable t) {
            Log.err(TAG, "sendEnergyByAction err:", t);
        }
    }

    private void popupTask() {
        try {
            JSONObject resData = new JSONObject(AntForestRpcCall.popupTask());
            if (!MessageUtil.checkResultCode(TAG, resData)) {
                return;
            }
            JSONArray forestSignVOList = resData.optJSONArray("forestSignVOList");
            if (forestSignVOList != null) {
                for (int i = 0; i < forestSignVOList.length(); i++) {
                    JSONObject forestSignVO = forestSignVOList.getJSONObject(i);
                    String signId = forestSignVO.getString("signId");
                    String currentSignKey = forestSignVO.getString("currentSignKey");
                    JSONArray signRecords = forestSignVO.getJSONArray("signRecords");
                    for (int j = 0; j < signRecords.length(); j++) {
                        JSONObject signRecord = signRecords.getJSONObject(j);
                        String signKey = signRecord.getString("signKey");
                        if (signKey.equals(currentSignKey)) {
                            if (!signRecord.getBoolean("signed")) {
                                JSONObject resData2 = new JSONObject(AntForestRpcCall.antiepSign(signId, "ANTFOREST_ENERGY_SIGN", UserIdMap.getCurrentUid()));
                                if (MessageUtil.checkSuccess(TAG, resData2)) {
                                    Log.forest("过期能量💊[" + signRecord.getInt("awardCount") + "g]");
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "popupTask err:", t);
        }
    }

    private void waterFriendEnergy() {
        String taskUid = UserIdMap.getCurrentUid();
        int waterEnergy = WaterFriendType.waterEnergy[waterFriendType.getValue()];
        if (waterEnergy == 0) {
            return;
        }
        Map<String, Integer> friendMap = waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
            String uid = friendEntry.getKey();
            if (selfId.equals(uid)) {
                continue;
            }
            Integer waterCount = friendEntry.getValue();
            if (waterCount == null || waterCount <= 0) {
                continue;
            }
            if (waterCount > 3) {
                waterCount = 3;
            }
            if (Status.canWaterFriendToday(uid, waterCount)) {
                try {
                    JSONObject jo = new JSONObject(AntForestRpcCall.queryFriendHomePage(uid));
                    TimeUtil.sleep(100);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        String bizNo = jo.getString("bizNo");
                        KVNode<Integer, Boolean> waterCountKVNode = returnFriendWater(uid, bizNo, waterCount, waterEnergy);
                        waterCount = waterCountKVNode.getKey();
                        if (waterCount > 0) {
                            Status.waterFriendToday(uid, waterCount, taskUid);
                        }
                        if (!waterCountKVNode.getValue()) {
                            break;
                        }
                    }
                } catch (Throwable t) {
                    Log.err(TAG, "waterFriendEnergy err:", t);
                }
            }
        }
    }

    private KVNode<Integer, Boolean> returnFriendWater(String userId, String bizNo, int count, int waterEnergy) {
        if (bizNo == null || bizNo.isEmpty()) {
            return new KVNode<>(0, true);
        }
        int wateredTimes = 0;
        boolean isContinue = true;
        try {
            String s;
            JSONObject jo;
            int energyId = getEnergyId(waterEnergy);
            label:
            for (int waterCount = 1; waterCount <= count; waterCount++) {
                s = AntForestRpcCall.transferEnergy(userId, bizNo, energyId, waterFriendEnergySendChat.getValue() ? "Y" : "N");
                TimeUtil.sleep(1500);
                jo = new JSONObject(s);

                String resultCode = jo.getString("resultCode");
                switch (resultCode) {
                    case "SUCCESS":
                        //记录浇水次数
                        Status.wateringFriendToday(userId);
                        Statistics.addData(Statistics.DataType.WATERINGCOUNT, 1);
                        int currentEnergy = jo.getJSONObject("userBaseInfo").getInt("currentEnergy");
                        Log.forest("好友浇水🚿给[" + UserIdMap.getShowName(userId) + "]浇" + waterEnergy + "g#剩余能量[" + currentEnergy + "g]");
                        Toast.show("好友浇水🚿给[" + UserIdMap.getShowName(userId) + "]浇" + waterEnergy + "g");
                        wateredTimes++;
                        Statistics.addData(Statistics.DataType.WATERED, waterEnergy);
                        break;
                    case "WATERING_TIMES_LIMIT":
                        Log.record("好友浇水🚿今日给[" + UserIdMap.getMaskName(userId) + "]浇水已达上限");
                        wateredTimes = 3;
                        break label;
                    case "WATERING_USER_LIMIT":
                        Log.record("好友浇水🚿给[" + UserIdMap.getMaskName(userId) + "]浇水，" + jo.getString("resultDesc"));
                        wateredTimes = 3;
                        break label;
                    default:
                        Log.record("好友浇水🚿" + jo.getString("resultDesc"));
                        Log.i(jo.toString());
                        break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "returnFriendWater err:", t);
        }
        return new KVNode<>(wateredTimes, isContinue);
    }

    private int getEnergyId(int waterEnergy) {
        if (waterEnergy <= 0) {
            return 0;
        }
        if (waterEnergy >= 66) {
            return 42;
        }
        if (waterEnergy >= 33) {
            return 41;
        }
        if (waterEnergy >= 18) {
            return 40;
        }
        return 39;
    }

    private void doubleWaterFriendEnergy() {
        String taskUid = UserIdMap.getCurrentUid();
        int waterEnergy = WaterFriendType.waterEnergy[waterFriendType.getValue()];
        if (waterEnergy == 0) {
            return;
        }
        boolean reSet = true;
        Map<String, Integer> friendMap = waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
            String uid = friendEntry.getKey();
            if (selfId.equals(uid)) {
                continue;
            }
            Integer waterCount = friendEntry.getValue();
            if (waterCount == null || waterCount <= 0) {
                continue;
            }
            if (Status.canWaterFriendToday(uid, 3)) {
                reSet = false;
            }
        }
        if (reSet) {
            for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
                String uid = friendEntry.getKey();
                if (selfId.equals(uid)) {
                    continue;
                }
                Integer waterCount = friendEntry.getValue();
                if (waterCount == null || waterCount <= 0) {
                    continue;
                }
                //重置浇水次数
                Status.waterFriendToday(uid, 0, taskUid);
            }
            Log.record("好友浇水🚿今日给好友浇水状态已重置！");
            Status.flagToday("Forest::doubleWaterFriendEnergy");
        }
    }

    private void forestExtensions() {
        try {
            ExtensionsHandle.handleAlphaRequest("antForest", "extensions", usingProps);
        } catch (Throwable t) {
            Log.err(TAG, "forestExtensions err:", t);
        }
    }

    // skuId, sku
    Map<String, JSONObject> skuInfo = new HashMap<>();

    private void vitalityExchangeBenefit() {
        try {
            getAllSkuInfo();
            Map<String, Integer> exchangeList = vitality_ExchangeBenefitList.getValue();
            for (Map.Entry<String, Integer> entry : exchangeList.entrySet()) {
                String skuId = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count < 0) {
                    continue;
                }
                while (Status.canVitalityExchangeBenefitToday(skuId, count) && exchangeBenefit(skuId)) {
                    TimeUtil.sleep(3000);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "vitalityExchangeBenefit err:", t);
        }
    }

    private void vantiepSign() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray forestSignVOList = jo.getJSONArray("forestSignVOList");
            JSONObject forestSignVO = forestSignVOList.getJSONObject(0);
            String currentSignKey = forestSignVO.getString("currentSignKey"); // 当前签到的 key
            String signId = forestSignVO.getString("signId"); // 签到ID
            String sceneCode = forestSignVO.getString("sceneCode"); // 场景代码
            JSONArray signRecords = forestSignVO.getJSONArray("signRecords"); // 签到记录
            for (int i = 0; i < signRecords.length(); i++) { // 遍历签到记录
                JSONObject signRecord = signRecords.getJSONObject(i);
                String signKey = signRecord.getString("signKey");
                int awardCount = signRecord.getInt("awardCount");
                if (signKey.equals(currentSignKey) && !signRecord.getBoolean("signed")) {
                    JSONObject joSign = new JSONObject(AntForestRpcCall.antiepSign(signId, UserIdMap.getCurrentUid(), sceneCode));
                    TimeUtil.sleep(300); // 等待300毫秒
                    if (MessageUtil.checkSuccess(TAG + "森林签到失败:", joSign)) {
                        int continuousCount = joSign.getInt("continuousCount");
                        Log.forest("森林签到📆拯救第" + continuousCount + "天#复活[" + awardCount + "g能量]");
                        Statistics.addData(Statistics.DataType.COLLECTED, awardCount);
                        // return awardCount;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    private void queryCommonSign() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryCommonSign("ANTFOREST_GIFT7TH_SIGN_202506"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("forestSignVO")) {
                if (!Status.hasFlagToday("forest::CommonSign")) {
                    Status.flagToday("forest::CommonSign");
                    Log.forest("森林签到📆尚未检测到[森林7日签到数据]若出现数据立马为大人领取");
                }
                return;
            }
            JSONObject forestSignVO = jo.getJSONObject("forestSignVO");
            String currentSignKey = forestSignVO.getString("currentSignKey"); // 当前签到的 key
            String signId = forestSignVO.getString("signId"); // 签到ID
            String sceneCode = forestSignVO.getString("sceneCode"); // 场景代码
            JSONArray signRecords = forestSignVO.getJSONArray("signRecords"); // 签到记录
            for (int i = 0; i < signRecords.length(); i++) { // 遍历签到记录
                JSONObject signRecord = signRecords.getJSONObject(i);
                String signKey = signRecord.getString("signKey");
                int awardCount = signRecord.getInt("awardCount");
                String awardType = signRecord.getString("awardType");
                JSONObject extInfo = signRecord.getJSONObject("extInfo");
                String awardName = extInfo.getString("awardName");
                if (signKey.equals(currentSignKey) && !signRecord.getBoolean("signed")) {
                    JSONObject joSign = new JSONObject(AntForestRpcCall.antiepSign(signId, UserIdMap.getCurrentUid(), sceneCode));
                    TimeUtil.sleep(300); // 等待300毫秒
                    if (MessageUtil.checkSuccess(TAG + "森林7日签到:", joSign)) {
                        int continuousCount = joSign.getInt("continuousCount");
                        Log.forest("森林签到📆第" + continuousCount + "天#7日签到[" + awardName + "*" + awardCount + "]");
                        if (awardType.equals("ENERGY")) {
                            Statistics.addData(Statistics.DataType.COLLECTED, awardCount);
                        }
                        // return awardCount;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    private void vitalitySign() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.vitalitySign());
            TimeUtil.sleep(300);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int continuousCount = jo.getInt("continuousCount");
                int signAwardCount = jo.getInt("signAwardCount");
                Log.forest("森林任务📆签到[" + continuousCount + "天]奖励[" + signAwardCount + "活力值]");
            }
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    /**
     * 森林任务列表复核轮次上限：每轮都会重拉一次任务列表，服务端状态不推进时必须靠上限收敛
     * （领奖是幂等的，任务持续 FINISHED 时会一直返回成功，没有上限就会一直重拉）。
     * <p>与乐园限定任务（optionalPlayRoundLimit）、新版保护地（固定 20 轮）保持同一收敛约定。
     */
    private static final int TASK_LIST_MAX_ROUNDS = 20;

    private void queryTaskList() {
        queryTaskList("DNHZ_SL_college", "DAXUESHENG_SJK");
        queryTaskList("DXS_BHZ", "NENGLIANGZHAO_20230807");
        queryTaskList("DXS_JSQ", "JIASUQI_20230808");
        try {
            for (int round = 0; round < TASK_LIST_MAX_ROUNDS; round++) {
                boolean doubleCheck = false;
                JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                // 添加安全的空值判断
                if (!jo.has("forestTasksNew")) {
                    break;
                }
                JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
                if (forestTasksNew == null || forestTasksNew.length() == 0) {
                    break;
                }
                for (int i = 0; i < forestTasksNew.length(); i++) {
                    JSONObject forestTask = forestTasksNew.optJSONObject(i);
                    if (forestTask == null) {
                        continue;
                    }
                    JSONArray taskInfoList = forestTask.optJSONArray("taskInfoList");
                    if (taskInfoList == null) {
                        continue;
                    }
                    // 累积而非覆盖：多个任务组里只要有一组真推进了，就值得再拉一轮复核
                    if (doForsetTaskList(taskInfoList)) {
                        doubleCheck = true;
                    }
                }
                // 本轮没有任何推进：收敛收工
                if (!doubleCheck) {
                    break;
                }
            }
            for (int round = 0; round < TASK_LIST_MAX_ROUNDS; round++) {
                JSONObject jo = new JSONObject(AntForestRpcCall.listTaskopengreen());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                // 添加安全的空值判断
                if (!jo.has("taskInfoList")) {
                    return;
                }
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList == null || taskInfoList.length() == 0) {
                    return;
                }
                if (!doForsetTaskList(taskInfoList)) {
                    break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryTaskList err:", t);
        }
    }

    private Boolean doForsetTaskList(JSONArray taskInfoList) {
        boolean doubleCheck = false;
        try {
            if (taskInfoList == null || taskInfoList.length() == 0) {
                return doubleCheck;
            }
            for (int j = 0; j < taskInfoList.length(); j++) {
                JSONObject taskInfo = taskInfoList.getJSONObject(j);
                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                String taskType = taskBaseInfo.getString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", taskType);
                String sceneCode = taskBaseInfo.getString("sceneCode");
                String taskStatus = taskBaseInfo.getString("taskStatus");
                JSONArray childTaskTypeList = taskInfo.optJSONArray("childTaskTypeList");
                // 容器型父任务（taskNode=PARENT，如选教卡 ENERGY_XUANJIAO）：行为都在 childTaskTypeList 里，
                // 父任务自身只是一个任务组、没有完成接口。它的 taskStatus 常为 RECEIVED（任务组自身的奖已领），
                // 不能因此跳过子任务——否则子任务里「已完成但未领奖」的奖励永远领不到。
                if ("PARENT".equals(taskBaseInfo.optString("taskNode"))
                        && childTaskTypeList != null && childTaskTypeList.length() > 0) {
                    boolean changed;
                    if ("ENERGY_XUANJIAO".equals(taskType)) {
                        // 已完成子任务一律领奖（纯领奖、无副作用，不受开关约束）；待办的才需要开关去尝试完成
                        changed = doEnergySceneTask(childTaskTypeList, energySceneTask.getValue());
                    } else {
                        // 其余容器统一处理：先领「已完成但未领奖」的子任务，再按子任务自带的
                        // autoCompleteTask 标记尝试待办项。不能按 taskType 白名单挑着做——
                        // 未列举的容器（如「逛农场得落叶肥料」）会被静默跳过，子任务永远没人领。
                        changed = awardFinishedChildren(childTaskTypeList);
                        changed = doChildTask(childTaskTypeList, taskTitle) || changed;
                    }
                    // 容器父任务自身的奖励同样要领：子任务可能都已领取，父任务却仍是 FINISHED 待领，
                    // 直接 continue 会把它永远漏掉。纯领奖、无副作用，不受开关约束。
                    if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                        changed = receiveTaskAward(sceneCode, taskType, taskTitle) || changed;
                    }
                    // 只有真动了才再核一遍。容器型父任务每轮都在列表里，无条件置 true 会让上层的
                    // while(doubleCheck) 空转。
                    if (changed) {
                        doubleCheck = true;
                    }
                    continue;
                }
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    if (receiveTaskAward(sceneCode, taskType, taskTitle)) {
                        doubleCheck = true;
                    }
                } else if (TaskStatus.TODO.name().equals(taskStatus)) {
                    //黑名单任务跳过
                    if (AntForestVitalityTaskList.getValue().contains(taskTitle)) {
                        continue;
                    }
                    // 周期/持续型任务：由真实行为推进、常驻列表且无 RPC 可完成，跳过（不尝试、不拉黑）
                    if (TaskAttemptPolicy.isCyclicTask(taskBaseInfo)) {
                        // 周期任务常驻列表、由真实行为推进，每轮结果相同；静默跳过
                        continue;
                    }

                    // 做不了的当天只试一次、临时故障留待下一轮（见 TaskAttemptPolicy）。
                    // 带活力值探针：伪申报后由探针重拉列表验证，避免"发过但不知成没成"
                    Outcome outcome = TaskAttemptPolicy.handle("forest::" + sceneCode + "/" + taskType, taskTitle, null,
                            () -> attemptFinishTask(sceneCode, taskType, taskTitle), Log::forest,
                            new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林任务", taskType, sceneCode,
                                    (TaskAttemptPolicy.StatusProbe) (k) -> probeForestVitalityStatus(sceneCode, taskType)));
                    if (outcome == Outcome.DONE || outcome == Outcome.TRIGGERED) {
                        doubleCheck = true;
                    }
                }
            }
            //可能是触发限时挑战奖励的
            AntForestRpcCall.batchQueryAndTouchopengreen();
        } catch (Throwable t) {
            Log.err(TAG, "doForsetTaskList err:", t);
        }
        return doubleCheck;
    }


    private void queryTaskList(String firstTaskType, String taskType) {
        if (Status.hasFlagToday("vitalityTask::" + firstTaskType)) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList(new JSONObject().put("firstTaskType", firstTaskType)));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 添加安全的空值判断
            if (!jo.has("forestTasksNew")) {
                return;
            }
            JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
            if (forestTasksNew == null || forestTasksNew.length() == 0) {
                return;
            }
            JSONObject firstTask = forestTasksNew.optJSONObject(0);
            if (firstTask == null || !firstTask.has("taskInfoList")) {
                return;
            }
            JSONArray taskInfoList = firstTask.optJSONArray("taskInfoList");
            if (taskInfoList == null || taskInfoList.length() == 0) {
                return;
            }
            for (int i = 0; i < taskInfoList.length(); i++) {
                jo = taskInfoList.getJSONObject(i).getJSONObject("taskBaseInfo");
                if (!Objects.equals(taskType, jo.getString("taskType"))) {
                    continue;
                }
                boolean isReceived = TaskStatus.RECEIVED.name().equals(jo.getString("taskStatus"));
                if (!isReceived && TaskStatus.FINISHED.name().equals(jo.getString("taskStatus"))) {
                    String sceneCode = jo.getString("sceneCode");
                    String taskTitle = new JSONObject(jo.getString("bizInfo")).getString("taskTitle");
                    isReceived = receiveTaskAward(sceneCode, taskType, taskTitle);
                    TimeUtil.sleep(1000);
                }
                if (isReceived) {
                    Status.flagToday("vitalityTask::" + firstTaskType);
                }
                return;
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryTaskList err:", t);
        }
    }

    private Boolean receiveTaskAward(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.receiveTaskAward(sceneCode, taskType));
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int incAwardCount = jo.optInt("incAwardCount", 1);
                if (jo.has("returnData")) {
                    String returnData = jo.optString("returnData");
                    if (returnData.contains("Energy")) {
                        Log.forest("森林任务🎖️领取[" + taskTitle + "]奖励#获得[" + incAwardCount + "g能量]");
                        Statistics.addData(Statistics.DataType.COLLECTED, incAwardCount);
                    } else {
                        Log.forest("森林任务🎖️领取[" + taskTitle + "]奖励#获得[" + incAwardCount + "活力值]");
                    }
                }
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveTaskAward err:", t);
        }
        return false;
    }

    /**
     * 黑名单键：剥掉标题末尾的 "(n/N)" 次数后缀。
     * <p>权限类任务在调用 {@link #finishTask} 时标题会被拼上 "(2/10)"，
     * 而运行时的黑名单检查用的是纯标题；不处理会导致写进黑名单的键永远匹配不上、拉黑失效。
     */
    private static String blackTaskKey(String taskTitle) {
        return StringUtil.stripCountSuffix(taskTitle);
    }

    private Boolean finishTask(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.finishTask(sceneCode, taskType));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntForestVitalityTaskList", blackTaskKey(taskTitle), jo);
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("森林任务🧾️完成[" + taskTitle + "]");
                return true;
            }
            // 另一种实现方案（见 TaskAlternative）
            if (TaskAlternative.hit(jo, sceneCode)) {
                TaskAlternative.trigger(null, taskType, taskTitle, taskType, sceneCode, "森林任务", msg -> Log.forest(msg));
                return false;
            }
            Log.record("完成任务[" + taskTitle + "]失败");
        } catch (Throwable t) {
            Log.err(TAG, "finishTask err:", t);
        }
        return false;
    }

    /** 领「已完成但未领奖」的容器子任务；纯领奖、无副作用。 */
    private boolean awardFinishedChildren(JSONArray childTaskTypeList) {
        boolean awarded = false;
        try {
            for (int i = 0; i < childTaskTypeList.length(); i++) {
                JSONObject taskInfo = childTaskTypeList.getJSONObject(i);
                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                if (!TaskStatus.FINISHED.name().equals(taskBaseInfo.optString("taskStatus"))) {
                    continue;
                }
                JSONObject bizInfo = new JSONObject(taskBaseInfo.optString("bizInfo", "{}"));
                String taskType = taskBaseInfo.optString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", taskType);
                String sceneCode = taskBaseInfo.optString("sceneCode");
                if (receiveTaskAward(sceneCode, taskType, taskTitle)) {
                    awarded = true;
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "awardFinishedChildren err:", th);
        }
        return awarded;
    }

    /** @return 本轮是否真上报了子任务（供上层判断要不要再核一遍列表） */
    private boolean doChildTask(JSONArray childTaskTypeList, String title) {
        boolean changed = false;
        try {
            for (int i = 0; i < childTaskTypeList.length(); i++) {
                JSONObject taskInfo = childTaskTypeList.getJSONObject(i);
                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                String taskType = taskBaseInfo.getString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", title);
                String sceneCode = taskBaseInfo.getString("sceneCode");
                String taskStatus = taskBaseInfo.getString("taskStatus");
                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    if (bizInfo.optBoolean("autoCompleteTask")) {
                        // 走统一策略：400000040 时由 TaskAttemptPolicy 代为 doFarmTask 伪申报，
                        // 裸调 finishTask 会把 UNSUPPORTED 吞成 false，兜底永不触发
                        TaskAttemptPolicy.handle("forest::child::" + sceneCode + "/" + taskType, taskTitle, null,
                                () -> attemptFinishTask(sceneCode, taskType, taskTitle), Log::forest,
                                new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林任务",
                                        taskType, sceneCode));
                        changed = true;
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "doChildTask err:", th);
        }
        return changed;
    }

    private boolean doEnergySceneTask(JSONArray childTaskTypeList, boolean allowAttempt) {
        boolean changed = false;
        int awarded = 0;
        int attempted = 0;
        try {
            for (int i = 0; i < childTaskTypeList.length(); i++) {
                JSONObject taskInfo = childTaskTypeList.getJSONObject(i);
                JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
                JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
                String taskType = taskBaseInfo.getString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", taskType);
                String taskStatus = taskBaseInfo.getString("taskStatus");
                String sceneCode = taskBaseInfo.getString("sceneCode");
                Outcome outcome;
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    outcome = TaskAttemptPolicy.handle(sceneCode + "/" + taskType, taskTitle,
                            () -> receiveTaskAward(sceneCode, taskType, taskTitle), null, Log::forest,
                            new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林任务", taskType, sceneCode));
                } else if (allowAttempt && TaskStatus.TODO.name().equals(taskStatus)) {
                    // 不传 probe：这类子任务都是「真实行为型」（缴费/出行/回收等），finishTask 必然失败，
                    // 失败原因由 attemptSceneTask 的返回值（UNSUPPORTED/UNABLE）已说明清楚。再挂 probe 只会
                    // 让每个子任务多拉一次任务列表（外加 1.5s 延迟），单轮发出成百次无谓请求。
                    outcome = TaskAttemptPolicy.handle(sceneCode + "/" + taskType, taskTitle, null,
                            () -> attemptSceneTask(sceneCode, taskType, taskTitle), Log::forest,
                            new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林任务", taskType, sceneCode));
                } else {
                    continue;
                }
                // 领到奖 / 上报成功 / 已交兜底，都要再核一遍列表（新判定完成的子任务要能接着领）
                if (outcome == Outcome.AWARDED) {
                    awarded++;
                } else if (outcome == Outcome.TRIGGERED || outcome == Outcome.DONE) {
                    attempted++;
                }
                changed = changed || outcome == Outcome.AWARDED || outcome == Outcome.TRIGGERED
                        || outcome == Outcome.DONE;
            }
            // 只在真动了时记一行（原来每轮无条件记，配合上层复核会刷屏）
            if (changed) {
                Log.other("场景卡[ENERGY_XUANJIAO]#共" + childTaskTypeList.length() + "个子任务#领奖"
                        + awarded + "#上报" + attempted);
            }
        } catch (Throwable t) {
            Log.err(TAG, "doEnergySceneTask err:", t);
        }
        return changed;
    }

    /**
     * 场景卡子任务的完成上报：不写任务黑名单（失败只代表本次未完成），外部场景类失败交给兜底方案；
     * 结论按 {@link Outcome} 分类，由 {@link TaskAttemptPolicy} 决定当天是否再试。
     */
    private Outcome attemptSceneTask(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.finishTask(sceneCode, taskType));
            //检查并标记黑名单任务：兜底方案也做不了的同样按该规则记账（400000040 降级为连续确认）
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntForestVitalityTaskList", blackTaskKey(taskTitle), jo);
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("森林任务🧾️完成[" + taskTitle + "]");
                return Outcome.DONE;
            }
            if (MessageUtil.isRetryable(jo) || MessageUtil.isServerBusy(jo)) {
                return Outcome.RETRY;
            }
            // 不支持rpc调用（400000040）→ 由 TaskAttemptPolicy 代为伪申报
            if (TaskAlternative.hit(jo, sceneCode)) {
                return Outcome.UNSUPPORTED;
            }
            Log.forest("完成任务[" + taskTitle + "]失败");
            return Outcome.UNABLE;
        } catch (Throwable t) {
            Log.err(TAG, "attemptSceneTask err:", t);
        }
        return Outcome.RETRY;
    }

    private void startEnergyRain() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.startEnergyRain());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String token = jo.getString("token");
            JSONArray bubbleEnergyList = jo.getJSONObject("difficultyInfo").getJSONArray("bubbleEnergyList");
            int sum = 0;
            for (int i = 0; i < bubbleEnergyList.length(); i++) {
                sum += bubbleEnergyList.getInt(i);
            }
            TimeUtil.sleep(5000L);
            if (sum == 50) {
                Status.flagToday("EnergyRain::PlayGame");
            }
            jo = new JSONObject(AntForestRpcCall.energyRainSettlement(sum, token));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Toast.show("获得了[" + sum + "g]能量[能量雨]");
                Log.forest("收能量雨🌧️[" + sum + "g]");
                totalCollected += sum;
                Statistics.addData(Statistics.DataType.COLLECTED, sum);
            }
            TimeUtil.sleep(500);
        } catch (Throwable th) {
            Log.err(TAG, "startEnergyRain err:", th);
        }
    }

    // LIMIT_TIME_ENERGY_DOUBLE_CLICK,CR20230516000363
    // LIMIT_TIME_ENERGY_RAIN_CHANCE,SK20250117005985,VITALITY_ENERGYRAIN_3DAYS，限时3天内使用能量雨次卡
    private void useEnergyRainCard() {
        try {
            // 商店兑换 限时能量雨卡
            exchangeBenefit("SK20250117005985");
            TimeUtil.sleep(2000);
            JSONObject jo;
            do {
                TimeUtil.sleep(1000);
                // 背包查找 能量雨卡
                jo = null;
                List<JSONObject> list = getPropGroup(getForestPropVOList(), PropGroup.energyRain.name());
                if (!list.isEmpty()) {
                    jo = list.get(0);
                }
                if (jo == null) {
                    break;
                }
                // 使用能量雨卡
            } while (consumeProp(jo));
        } catch (Throwable th) {
            Log.err(TAG, "useEnergyRainCard err:", th);
        }
    }

    private void energyRain() {
        try {
            JSONObject joEnergyRainHome = new JSONObject(AntForestRpcCall.queryEnergyRainHome());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, joEnergyRainHome)) {
                if (joEnergyRainHome.getBoolean("canPlayToday")) {
                    startEnergyRain();
                }
                if (joEnergyRainHome.getBoolean("canGrantStatus")) {
                    Log.record("有送能量雨的机会");
                    JSONObject joEnergyRainCanGrantList = new JSONObject(AntForestRpcCall.queryEnergyRainCanGrantList());
                    TimeUtil.sleep(500);
                    JSONArray grantInfos = joEnergyRainCanGrantList.getJSONArray("grantInfos");
                    Set<String> set = giveEnergyRainList.getValue();
                    String userId;
                    boolean granted = false;
                    for (int j = 0; j < grantInfos.length(); j++) {
                        JSONObject grantInfo = grantInfos.getJSONObject(j);
                        if (grantInfo.getBoolean("canGrantedStatus")) {
                            userId = grantInfo.getString("userId");
                            if (set.contains(userId)) {
                                JSONObject joEnergyRainChance = new JSONObject(AntForestRpcCall.grantEnergyRainChance(userId));
                                TimeUtil.sleep(500);
                                Log.record("尝试送能量雨给【" + UserIdMap.getMaskName(userId) + "】");
                                granted = true;
                                // 20230724能量雨调整为列表中没有可赠送的好友则不赠送
                                if (MessageUtil.checkResultCode(TAG, joEnergyRainChance)) {
                                    Log.forest("送能量雨🌧️[" + UserIdMap.getMaskName(userId) + "]");
                                    startEnergyRain();
                                }
                                break;
                            }
                        }
                    }
                    if (!granted) {
                        Log.record("没有可以送的用户");
                    }
                }
                boolean canPlayGame = joEnergyRainHome.getBoolean("canPlayGame");

                if (canPlayGame) {
                    // 检查今日是否需要执行
                    if (!Status.hasFlagToday("EnergyRain::PlayGame")) {
                        Log.record("是否可以能量雨游戏: " + canPlayGame);
                        // 检查并处理游戏任务
                        checkAndDoEndGameTask();
                        TimeUtil.sleep(4000);
                    }
                }
            }
            joEnergyRainHome = new JSONObject(AntForestRpcCall.queryEnergyRainHome());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, joEnergyRainHome) && joEnergyRainHome.getBoolean("canPlayToday")) {
                startEnergyRain();
            }
        } catch (Throwable th) {
            Log.err(TAG, "energyRain err:", th);
        }
    }

    public static void checkAndDoEndGameTask() {
        try {
            // 1. 查询游戏任务列表
            String response = AntForestRpcCall.queryEnergyRainEndGameList();
            JSONObject jo = new JSONObject(response);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("energyRainEndGameGroupTask")) {
                // 响应里没有任务组：可能是接口异常/活动未下发，取不到状态不落标记，留给下一轮
                Log.record("森林救援队🐱查询响应无任务组，不落标记，留待下一轮");
                return;
            }

            // 2. 初始化新任务（需要接入森林救援队）
            if (jo.optBoolean("needInitTask", false)) {
                Log.record("检测到新任务，准备接入[森林救援队]...");
                String initResStr = AntForestRpcCall.initTask("GAME_DONE_SLJYD");
                JSONObject initRes = new JSONObject(initResStr);
                if (!MessageUtil.checkResultCode(TAG, initRes)) {
                    return;
                }
            }

            // 3. 仅当森林救援队(GAME_DONE_SLJYD)未完结时才上报，避免每轮重复发起外部请求
            JSONObject groupTask = jo.optJSONObject("energyRainEndGameGroupTask");
            JSONArray taskInfoList = groupTask != null ? groupTask.optJSONArray("taskInfoList") : null;

            String sljydStatus = null;
            if (taskInfoList != null) {
                for (int i = 0; i < taskInfoList.length(); i++) {
                    JSONObject task = taskInfoList.optJSONObject(i);
                    JSONObject baseInfo = task != null ? task.optJSONObject("taskBaseInfo") : null;
                    if (baseInfo == null) continue;
                    if ("GAME_DONE_SLJYD".equals(baseInfo.optString("taskType"))) {
                        sljydStatus = baseInfo.optString("taskStatus");
                        break;
                    }
                }
            }

            // 需要初始化、或任务处于待办/未触发时上报；已完结/无任务则仅标记今日已处理
            boolean needInit = jo.optBoolean("needInitTask", false);
            boolean shouldReport = needInit
                    || "TODO".equals(sljydStatus)
                    || "NOT_TRIGGER".equals(sljydStatus);

            if (shouldReport) {
                // 上报后用同步版等待结果，只有成功上报且回读确认服务端状态推进才落当日标记；
                // 上报失败/回读取不到状态一律不落标记，留给下一轮再试
                int successes = GameTask.Forest_sljyd.reportSync("森林", 1);
                if (successes <= 0) {
                    Log.record("森林救援队🐱上报未成功(成功" + successes + "次)，不落标记，留待下一轮");
                    return;
                }

                // 上报成功后回读一次任务状态，确认服务端已推进
                String afterStatus = null;
                try {
                    JSONObject recheckJo = new JSONObject(AntForestRpcCall.queryEnergyRainEndGameList());
                    if (MessageUtil.checkResultCode(TAG, recheckJo)) {
                        JSONObject recheckGroupTask = recheckJo.optJSONObject("energyRainEndGameGroupTask");
                        JSONArray recheckTaskInfoList = recheckGroupTask != null ? recheckGroupTask.optJSONArray("taskInfoList") : null;
                        if (recheckTaskInfoList != null) {
                            for (int i = 0; i < recheckTaskInfoList.length(); i++) {
                                JSONObject task = recheckTaskInfoList.optJSONObject(i);
                                JSONObject baseInfo = task != null ? task.optJSONObject("taskBaseInfo") : null;
                                if (baseInfo != null && "GAME_DONE_SLJYD".equals(baseInfo.optString("taskType"))) {
                                    afterStatus = baseInfo.optString("taskStatus");
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable th) {
                    Log.printStackTrace("森林救援队🐱回读确认失败:", th);
                }

                if (afterStatus == null) {
                    Log.record("森林救援队🐱上报成功但回读取不到状态，不落标记，留待下一轮确认");
                    return;
                }
                boolean progressed = !"TODO".equals(afterStatus) && !"NOT_TRIGGER".equals(afterStatus);
                if (progressed) {
                    Log.record("森林救援队🐱上报成功，回读状态[" + afterStatus + "]已推进，今日标记完成");
                    Status.flagToday("EnergyRain::PlayGame");
                } else {
                    Log.record("森林救援队🐱上报成功但回读状态[" + afterStatus + "]未推进，不落标记，留待下一轮");
                }
                return;
            }
            Log.record("森林救援队🐱无需上报(状态:" + sljydStatus + ")，今日跳过");
            Status.flagToday("EnergyRain::PlayGame");

        } catch (Throwable th) {
            Log.printStackTrace("执行能量雨后续任务出错:", th);
        }
    }

    public void doforestgame() {
        try {
            String response = AntForestRpcCall.queryGameList();
            JSONObject jo = new JSONObject(response);

            // 验证请求是否成功
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.error("queryGameList 失败: " + jo.optString("desc"));
                return;
            }

            JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
            if (drawRights != null) {
                int perTime = drawRights.optInt("quotaPerTime", 100);

                // 换算实际宝箱次数
                int canUseCount = drawRights.optInt("quotaCanUse") / perTime;
                int limitCount = drawRights.optInt("quotaLimit") / perTime;
                int usedCount = drawRights.optInt("usedQuota") / perTime;

                // 1. 处理待开启奖励 (批量开启)
                if (canUseCount > 0) {
                    Log.record("森林乐园正在一次性开启 " + canUseCount + " 个宝箱...");
                    JSONObject drawJo = new JSONObject(AntForestRpcCall.drawGameCenterAward(canUseCount));
                    if (!MessageUtil.checkResultCode(drawJo)) {
                        return;
                    }
                    JSONArray awardList = drawJo.optJSONArray("gameCenterDrawAwardList");
                    int totalEnergy = 0;
                    List<String> otherAwards = new ArrayList<>();

                    if (awardList != null) {
                        for (int i = 0; i < awardList.length(); i++) {
                            JSONObject award = awardList.getJSONObject(i);
                            String type = award.optString("awardType");
                            String name = award.optString("awardName");
                            int count = award.optInt("awardCount");
                            Log.forest("森林乐园🎁开宝箱得[" + name + "*" + count + "]");
                            if ("ENERGY".equals(type)) {
                                totalEnergy += count;
                            } else {
                                otherAwards.add(name + "x" + count);
                            }
                        }
                    }
                    Statistics.addData(Statistics.DataType.COLLECTED, totalEnergy);
                    // 输出统计结果
                    StringBuilder logMsg = new StringBuilder("森林乐园🎁[开宝箱]共计");
                    if (totalEnergy > 0) {
                        logMsg.append("获得能量").append(totalEnergy).append("g");
                    }
                    if (!otherAwards.isEmpty()) {
                        if (totalEnergy > 0) {
                            logMsg.append(", ");
                        }
                        logMsg.append("其他: ").append(String.join("/", otherAwards));
                    }
                    Log.forest(logMsg.toString());
                    Toast.show(logMsg.toString());

                }

                // 2. 判断是否需要刷任务
                int remainToTask = limitCount - usedCount;
                if (remainToTask > 0) {
                    GameTask.Forest_slxcc.report("森林", remainToTask);
                } else {
                    Log.record("今日森林乐园游戏任务已满额");
                }
            }

        } catch (CancellationException e) {
            throw e;
        } catch (Throwable t) {
            Log.printStackTrace("doforestgame 流程异常", t);
        }
    }

    //乐园限定活动
    private void queryOptionalPlay() {
        try {
            // 每个任务的每个阶段（taskType#rightsTimes）本轮最多动作一次：同一阶段重复动作会白跑，
            // 但阶段推进（领取后 rightsTimes 递增）要允许再做，故去重键带序号。
            Set<String> attempted = new HashSet<>();
            int round = 0;
            // 首轮按实际任务阶段数算定上限（见 optionalPlayRoundLimit），之后不再变
            int roundLimit = 0;
            while (round++ < roundLimit || roundLimit == 0) {
                JSONObject jo = new JSONObject(AntForestRpcCall.queryOptionalPlay());
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    Log.forest("森林乐园⚠️任务列表拉取失败[" + TaskAlternative.describe(jo) + "]");
                    return;
                }
                if (!jo.has("taskTriggerPlayInfo")) {
                    Log.forest("森林乐园⚠️任务列表缺少任务信息");
                    return;
                }
                JSONObject taskTriggerPlayInfo = jo.optJSONObject("taskTriggerPlayInfo");
                if (taskTriggerPlayInfo == null || !taskTriggerPlayInfo.has("taskList")) {
                    Log.forest("森林乐园⚠️任务列表缺少任务明细");
                    return;
                }
                JSONArray taskList = taskTriggerPlayInfo.getJSONArray("taskList");
                // 账号当前没有乐园限定任务，直接收工
                if (taskList.length() == 0) {
                    Log.forest("森林乐园🎈当前无限定任务");
                    return;
                }
                if (roundLimit == 0) {
                    roundLimit = optionalPlayRoundLimit(taskList);
                }
                // 本轮是否还有新的动作（不含已试过的），没有就收工
                boolean progressed = false;

                for (int j = 0; j < taskList.length(); j++) {
                    JSONObject task = taskList.getJSONObject(j);
                    String taskStatus = task.getString("taskStatus");
                    int alreadyReceiveAwardCount = task.optInt("alreadyReceiveAwardCount");
                    int awardCount = task.optInt("awardCount");
                    int awardCountForReceive = awardCount - alreadyReceiveAwardCount;
                    int rightsTimesLimit = task.optInt("rightsTimesLimit");
                    int rightsTimes = task.optInt("rightsTimes");
                    String awardType = task.optString("awardType", "能量");
                    JSONObject bizInfo = task.optJSONObject("bizInfo");
                    if (bizInfo == null) {
                        continue;
                    }
                    String title = bizInfo.optString("title");
                    String source = task.optString("source", "ch_appcenter__chsub_9patch");
                    String sceneCode = task.optString("sceneCode", "");
                    String taskType = task.optString("taskType", "");
                    String groupId = task.optString("groupId", "");
                    // 开箱类任务的完成条件是**服务端的开箱计数**，申报完成不会让计数推进
                    // （申报只会被拒，伪申报被拒后又会被判"兜底未生效"拉黑），只能真去开箱
                    String actionType = bizInfo.optString("actionType", "");
                    // 去重键带轮次与阶段序号：同一轮内同阶段只动作一次（防重复请求），
                    // 但下一轮可再试——服务端状态推进有延迟，跨轮仍可能落到新阶段
                    String attemptKey = taskType + "#" + rightsTimes + "@" + round;
                    // 领奖去重**不带轮次**且**跨调用生效**：发奖是幂等的，服务端对已领过的任务仍会成功
                    // 返回同一个 incAwardCount；只按轮次或单次调用去重会让重跑的每一轮都重复领一次、
                    // 把同一笔能量反复记账。故按"任务+阶段"打当日标记，一天只领一次。
                    String awardKey = "forest::optionalplay::award::" + sceneCode + "/" + taskType
                            + "#" + rightsTimes;

                    // 黑名单任务跳过
                    if (AntForestVitalityTaskList.getValue().contains(title)) {
                        continue;
                    }
                    // 底线：硬交易/支付/充值类一律不申报、一次即永久拉黑，绝不伪造。
                    // 必须同时查 groupId：充值类的 taskType 常无关键词，判据在 groupId（cz=充值）
                    // 软判据（_cz 数字等疑似充值）放行：限定活动实测部分可通过伪申报完成，
                    // 失败/风控由 TaskAttemptPolicy 的自动黑名单兜底。
                    if (TaskAlternative.isTransactionTaskHard(taskType)
                            || TaskAlternative.isTransactionTaskHard(groupId)
                            || TaskAlternative.isTransactionTaskHard(sceneCode)) {
                        // 同一轮只拉一次：该分支不做去重的话，多轮重拉会把同一批任务反复写盘并刷屏
                        if (attempted.add(blackTaskKey(title))) {
                            MessageUtil.MarkTaskBlackListPermanent("AntForestV2", "AntForestVitalityTaskList",
                                    "森林乐园限定任务", blackTaskKey(title));
                            Log.forest("森林乐园⏭️交易/履约类[" + title + "]#不申报，已永久拉黑");
                        }
                        continue;
                    }

                    // 已完成待领：领奖。已领满（awardCountForReceive<=0）不再重复请求；
                    // 当日已领过的同一阶段也不再重复请求（见 awardKey 注释：重复领会造成统计虚高）
                    if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                        if (awardCountForReceive > 0 && !Status.hasFlagToday(awardKey)) {
                            progressed = true;
                            JSONObject joReceived = new JSONObject(AntForestRpcCall.receiveTaskAwardopengreen(source, sceneCode, taskType));
                            if (MessageUtil.checkSuccess(TAG, joReceived)) {
                                Status.flagToday(awardKey);
                                int incAwardCount = joReceived.optInt("incAwardCount");
                                JSONObject taskConfigResultVO = joReceived.optJSONObject("taskConfigResultVO");
                                if (taskConfigResultVO != null) {
                                    awardType = taskConfigResultVO.optString("awardType", awardType);
                                }
                                // 能量统计
                                if ("能量".equals(awardType) || "ENERGY".equals(awardType)) {
                                    Log.forest("森林乐园🎖️领取[" + title + "]奖励[" + incAwardCount + "g能量]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, incAwardCount);
                                } else {
                                    Log.forest("森林乐园🎖️领取[" + title + "]奖励[" + awardType + "*" + incAwardCount + "]");
                                }
                            }
                        }
                        continue;
                    }

                    // 待完成 / 多阶段未满：上报完成
                    if (TaskStatus.TODO.name().equals(taskStatus) || rightsTimes < rightsTimesLimit) {
                        if (!attempted.add(attemptKey)) {
                            continue;
                        }
                        String label = rightsTimesLimit > 1
                                ? title + "(" + (rightsTimes + 1) + "/" + rightsTimesLimit + ")" : title;
                        // key 必须带轮次：多次任务每推进一档都是**新的**尝试，不带轮次会让第一档打上的
                        // 当日标记把后续档位全挡下、任务停在第一档。单次任务保持原 key，语义不变。
                        String policyKey = "forest::optionalplay::" + sceneCode + "/" + taskType
                                + (rightsTimesLimit > 1 ? "#" + rightsTimes : "");
                        // 走统一策略：400000040 时由 TaskAttemptPolicy 代为 doFarmTask 伪申报，
                        // 裸调 finishTask 会把 UNSUPPORTED 吞成 false，兜底永不触发
                        // 开箱类任务的尝试动作 = 真去开箱（申报完成对它是无效动作）；
                        // 其余任务仍走 finishTask 上报
                        final boolean unwrapTask = isUnwrapAction(actionType);
                        Outcome outcome = TaskAttemptPolicy.handle(
                                policyKey, label, null,
                                () -> unwrapTask
                                        ? openForestLeyuanBoxes(title)
                                        : attemptFinishTask(sceneCode, taskType, label), Log::forest,
                                new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林乐园限定任务",
                                        taskType, sceneCode, TaskAlternative.DEFAULT_VERSION, null,
                                        (TaskAttemptPolicy.ExhaustedProbe) (k) -> probeOptionalPlayExhausted(sceneCode, taskType)));
                        // 被当日标记挡下（已试过）不算推进，否则每轮都白转一遍
                        if (outcome != Outcome.TRIED_TODAY && outcome != Outcome.SKIPPED) {
                            progressed = true;
                        }
                    }
                }

                // 一整轮里既无可领也无可做：可重复任务已打满次数，收工
                if (!progressed) {
                    break;
                }
                // 服务端状态推进有延迟，留出落态时间再重拉
                TimeUtil.sleep(1500);
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryOptionalPlay err:", th);
        }
    }

    /** 开箱类动作（累计开箱 N 个）：完成条件在服务端的开箱计数上，只能真开箱，申报无效 */
    private static boolean isUnwrapAction(String actionType) {
        return "ACC_UNWRAP".equals(actionType) || "UNWRAP".equals(actionType);
    }

    /**
     * 开箱类乐园任务的尝试动作：把当前可开宝箱一次开完。
     * <p>只开**待开的**（{@code quotaCanUse}），没有可开的就直接返回 UNABLE——
     * 此时任务没完成是因为宝箱不够，不是接口不行，故让策略按"未完成"处理而不是伪申报。
     */
    private Outcome openForestLeyuanBoxes(String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryGameList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return Outcome.RETRY;
            }
            JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
            if (drawRights == null) {
                return Outcome.RETRY;
            }
            int perTime = drawRights.optInt("quotaPerTime", 100);
            int canUseCount = perTime <= 0 ? 0 : drawRights.optInt("quotaCanUse") / perTime;
            if (canUseCount <= 0) {
                // 没有可开的箱子：完成不了是"箱子不够"，不是接口不支持，不能退到伪申报
                Log.forest("森林乐园🎁暂无可开宝箱#待攒够箱子");
                return Outcome.UNABLE;
            }
            JSONObject drawJo = new JSONObject(AntForestRpcCall.drawGameCenterAward(canUseCount));
            if (!MessageUtil.checkResultCode(TAG, drawJo)) {
                return MessageUtil.isRetryable(drawJo) || MessageUtil.isServerBusy(drawJo)
                        ? Outcome.RETRY : Outcome.UNABLE;
            }
            Log.forest("森林乐园🎁开宝箱×" + canUseCount + "#用于[" + taskTitle + "]");
            return Outcome.DONE;
        } catch (Throwable t) {
            Log.err(TAG, "openForestLeyuanBoxes err:", t);
        }
        return Outcome.RETRY;
    }

    /** 乐园限定任务单轮最大重拉次数：正常由「本轮无任何可执行/可领取动作」提前收工，
     * 此上限只防服务端恒回 TODO 时死循环。
     * <p>必须按**各任务阶段数之和**给足：每次重拉每个任务最多推进一档，多个多阶段任务
     * 串行推进，固定值会在任务没做满时就收工。
     */
    private int optionalPlayRoundLimit(JSONArray taskList) {
        int total = 0;
        for (int i = 0; i < taskList.length(); i++) {
            JSONObject task = taskList.optJSONObject(i);
            if (task == null) {
                continue;
            }
            int limit = task.optInt("rightsTimesLimit", 1);
            total += Math.max(limit, 1);
        }
        // 至少 12，最多 60：给足阶段数，也防任务列表异常时长时间空转
        return Math.min(Math.max(total + 6, 12), 60);
    }

    /** 活力值任务探针：重拉任务列表，读 {@code taskBaseInfo.taskStatus} 兜底验证（上游 v1.3.9 对齐）。 */
    private static TaskAttemptPolicy.ProbeResult probeForestVitalityStatus(String sceneCode, String taskType) {
        // 状态在 taskBaseInfo 子对象内
        return TaskAttemptPolicy.probeByList(TAG, AntForestRpcCall::listTaskopengreen, "taskInfoList",
                jo -> MessageUtil.checkResultCode(TAG, jo),
                taskInfo -> {
                    JSONObject taskBaseInfo = taskInfo.optJSONObject("taskBaseInfo");
                    if (taskBaseInfo == null
                            || !sceneCode.equals(taskBaseInfo.optString("sceneCode"))
                            || !taskType.equals(taskBaseInfo.optString("taskType"))) {
                        return null;
                    }
                    return taskBaseInfo.optString("taskStatus");
                });
    }

    /** 打满判据：重拉乐园限定任务列表，读该任务的已做次数是否达上限。 */
    private static boolean probeOptionalPlayExhausted(String sceneCode, String taskType) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryOptionalPlay());
            if (!MessageUtil.checkSuccess(TAG, jo) || !jo.has("taskTriggerPlayInfo")) {
                return false;
            }
            JSONObject playInfo = jo.optJSONObject("taskTriggerPlayInfo");
            JSONArray taskList = playInfo == null ? null : playInfo.optJSONArray("taskList");
            for (int i = 0; taskList != null && i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null || !taskType.equals(task.optString("taskType"))
                        || !sceneCode.equals(task.optString("sceneCode"))) {
                    continue;
                }
                int limit = task.optInt("rightsTimesLimit", 0);
                return limit > 0 && task.optInt("rightsTimes", 0) >= limit;
            }
        } catch (Throwable t) {
            Log.err(TAG, "probeOptionalPlayExhausted err:", t);
        }
        return false;
    }

    /** 统一策略的完成尝试：返回 Outcome，由 TaskAttemptPolicy 决定伪申报/当日标记/黑名单。 */
    private Outcome attemptFinishTask(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.finishTask(sceneCode, taskType));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntForestVitalityTaskList", blackTaskKey(taskTitle), jo);
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("森林任务🧾️完成[" + taskTitle + "]");
                return Outcome.DONE;
            }
            if (MessageUtil.isRetryable(jo) || MessageUtil.isServerBusy(jo)) {
                return Outcome.RETRY;
            }
            // 不支持rpc调用（400000040）→ 由 TaskAttemptPolicy 代为伪申报：逛一逛类外部场景
            // （GYG_* / TAOBAO_* / NONGCHANG_* 等，如森林图书馆、淘宝人生、科普视频、点淘、淘宝特价版、
            // 芭芭农场、摇一摇、买菜、薅羊毛）；原先按 taskType 维护的白名单已废弃，不再单独判断
            if (TaskAlternative.hit(jo, sceneCode)) {
                return Outcome.UNSUPPORTED;
            }
            Log.forest("完成任务[" + taskTitle + "]失败");
            return Outcome.UNABLE;
        } catch (Throwable t) {
            Log.err(TAG, "finishTask err:", t);
        }
        return Outcome.RETRY;
    }

    private void continuousUseCardOptions() {
        //双击卡
        continuousUseAndExchangeCard("doubleClick", "SK20240805004754");
        //收能量倍卡
        continuousUseAndExchangeCard("robExpandCard", "");
        //保护罩：自动续用为独立开关，不受「连续兑换使用道具卡片」选项限制
        if (autoUseShieldCard.getValue()) {
            continuousUseAndExchangeCard("shield", "CR20230516000370");
        }
        //隐身卡
        continuousUseAndExchangeCard("stealthCard", "SK20230521000206");
        //炸弹卡
        //continuousUseAndExchangeCard("energyBombCard", "SK20250219006517");
    }

    private void continuousUseAndExchangeCard(String propGroupType, String exchangeProp) {
        try {
            if (propGroupType.equals("shield") || continuousUseCardOptions.getValue().contains(propGroupType)) {
                long continuousUseCardSecond = continuousUseCardCheak(propGroupType);
                if (continuousUseCardSecond >= 0) {
                    TimeUtil.sleep(500);
                    JSONObject rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                    if (rightCard == null) {
                        if (exchangeProp != null) {
                            exchangeBenefit(exchangeProp);
                            TimeUtil.sleep(500);
                        }
                        rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                        if (rightCard == null) {
                            return;
                        }
                    }
                    int holdsNum = rightCard.optInt("holdsNum");
                    if (holdsNum == 0) {
                        return;
                    }
                    int loopCount = 0; // 循环次数计数
                    final int MAX_LOOP = 10; // 最大循环次数，避免死循环
                    do {
                        rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                        if (rightCard == null) {
                            return;
                        }
                        holdsNum = rightCard.optInt("holdsNum");
                        if (holdsNum == 0) {
                            return;
                        }
                        if (!rightCard.has("propIdList")) {
                            return;
                        }
                        JSONArray propIdList = rightCard.optJSONArray("propIdList");
                        if (propIdList.length() == 0) {
                            return;
                        }
                        String propId = propIdList.optString(0);
                        String propType = rightCard.optString("propType");
                        String propName = rightCard.getJSONObject("propConfigVO").getString("propName");
                        JSONObject joResult;
                        switch (propGroupType) {
                            case "doubleClick":
                            case "shield":
                            case "robExpandCard":
                                if (continuousUseCardSecond > 0) {
                                    joResult = new JSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType, true));
                                } else {
                                    joResult = new JSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType, false));
                                }
                                holdsNum--;
                                TimeUtil.sleep(500);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]");
                                }
                                break;

                            case "stealthCard":
                                joResult = new JSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType));
                                holdsNum--;
                                TimeUtil.sleep(1000);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]");
                                }
                                break;
                            /*case "energyBombCard":
                                joResult = new JSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType,false));
                                holdsNum--;
                                TimeUtil.sleep(1000);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]");
                                }
                                break;*/
                        }
                        continuousUseCardSecond = continuousUseCardCheak(propGroupType);
                        if (continuousUseCardSecond < 0) {
                            return;
                        }
                        TimeUtil.sleep(500);
                    } while (holdsNum > 0 && ++loopCount < MAX_LOOP);
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "continuousUseAndExchangeCard err:", th);
        }
    }

    /**
     * 该道具类型在"当前没在使用（接口里没出现 / 已经过期）"时，是否应该启用一张新的。
     * <p>只有保护罩需要这样处理：它的意义是让能量球不被偷，过期后必须再动一张新的，否则等于没保护。
     * 原先这两种情况都返回 -1（不可用），结果**一旦过期就永远不再使用**，这正是"不自动使用保护罩"
     * 的直接原因。其余道具保持原行为（隐身卡固定不使用；双击卡/倍率卡仍按阈值接续，改动其默认行为
     * 会增加不必要的道具消耗，不在本次范围内）。
     */
    private static boolean canStartWhenNotInUse(String propGroupType) {
        return "shield".equals(propGroupType);
    }

    //判断是否可以使用道具卡片
    //返回值-1为不可用，0为可用，大于0为剩余时间
    private long continuousUseCardCheak(String propGroupType) {
        try {
            JSONObject joMiscHomes = new JSONObject(AntForestRpcCall.queryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, joMiscHomes)) {
                return -1;
            }
            if (!joMiscHomes.has("combineHandlerVOMap")) {
                return -1;
            }
            long now = System.currentTimeMillis();
            JSONObject combineHandlerVOMap = joMiscHomes.optJSONObject("combineHandlerVOMap");
            if (!combineHandlerVOMap.has("usingProp")) {
                // 当前没有任何道具在使用：保护罩要能从头用一张，其余道具保持原行为（不可用）
                return canStartWhenNotInUse(propGroupType) ? 0 : -1;
            }
            JSONObject usingProp = combineHandlerVOMap.optJSONObject("usingProp");
            if (!usingProp.has("userPropVOS")) {
                return canStartWhenNotInUse(propGroupType) ? 0 : -1;
            }
            JSONArray userPropVOS = usingProp.getJSONArray("userPropVOS");
            for (int i = 0; i < userPropVOS.length(); i++) {
                JSONObject userPropVO = userPropVOS.getJSONObject(i);
                String propGroup = userPropVO.optString("propGroup");
                if (propGroup.equals(propGroupType)) {
                    long endTime = userPropVO.optLong("endTime");
                    long duringTime = endTime - now;
                    if (duringTime < 0) {
                        // 用过的道具已过期（接口里仍留着这条记录）：保护罩应重新启用一张
                        return canStartWhenNotInUse(propGroupType) ? 0 : -1;
                    }
                    switch (propGroupType) {
                        case "doubleClick":
                            if (duringTime / (1000 * 60) < 60 * 24 * 31) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        case "robExpandCard":
                            if (duringTime / (1000 * 60) < 60 * 24 * 30) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        case "stealthCard":
                            return -1;
                        case "shield":
                            if (duringTime / (1000 * 60) < 60 * continuousUseShieldHour.getValue()) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        /*case "energyBombCard":
                            if (duringTime / (1000 * 60) < 3*60 * 24) {
                                Log.forest("duringTime");
                                return duringTime;
                            }
                            else {
                                return -1;
                            }*/

                    }
                }
            }
            return 0;
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
        return -1;
    }

    private String useRobExpandCardFactor() {
        try {
            JSONObject joMiscHomes = new JSONObject(AntForestRpcCall.queryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, joMiscHomes)) {
                return null;
            }
            if (!joMiscHomes.has("combineHandlerVOMap")) {
                return null;
            }
            long now = System.currentTimeMillis();
            JSONObject combineHandlerVOMap = joMiscHomes.optJSONObject("combineHandlerVOMap");
            if (!combineHandlerVOMap.has("usingProp")) {
                return null;
            }
            JSONObject usingProp = combineHandlerVOMap.optJSONObject("usingProp");
            if (!usingProp.has("userPropVOS")) {
                return null;
            }
            JSONArray userPropVOS = usingProp.getJSONArray("userPropVOS");
            for (int i = 0; i < userPropVOS.length(); i++) {
                JSONObject userPropVO = userPropVOS.getJSONObject(i);
                String propGroup = userPropVO.optString("propGroup");
                if (propGroup.equals("robExpandCard")) {
                    if (!userPropVO.has("detail")) {
                        return null;
                    }
                    return userPropVO.optJSONObject("detail").optString("factor");
                }
            }
            return null;
        } catch (Throwable th) {
            Log.err(TAG, "UserobExpandCardFactor err:", th);
        }
        return null;
    }


    //选出可以使用的限时道具卡片
    private JSONObject chooseContinuousLIMITTIMECard(String propGroupType) {
        try {
            JSONArray forestPropVOList = getForestPropVOList();
            JSONObject rightCard = null;
            String useFactor = useRobExpandCardFactor();
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestBagProp = forestPropVOList.getJSONObject(i);
                String propGroup = forestBagProp.optString("propGroup");
                if (forestBagProp.has("recentExpireTime") && propGroup.equals(propGroupType)) {
                    switch (propGroup) {
                        case "stealthCard":
                        case "shield":
                        case "doubleClick":
                            //case "energyBombCard":
                            if (rightCard != null) {
                                long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                                long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                                if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                    rightCard = forestBagProp;
                                }
                            } else {
                                rightCard = forestBagProp;
                            }
                            break;
                        case "robExpandCard":
                            //有在用倍卡，选择同倍率快到期卡
                            if (useFactor != null) {
                                String factorforestBagProp = forestBagProp.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                                if (factorforestBagProp.equals(useFactor)) {
                                    if (rightCard != null) {
                                        String factorrightCard = rightCard.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                                        long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                                        long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                                        if (Float.parseFloat(factorrightCard) == Float.parseFloat(factorforestBagProp)) {
                                            if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                                rightCard = forestBagProp;
                                            }
                                        }
                                    } else {
                                        rightCard = forestBagProp;
                                    }
                                }
                            }
                            //没有在用倍卡，选择最高倍率卡
                            else {
                                if (rightCard != null) {
                                    String factorrightCard = rightCard.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                                    String factorforestBagProp = forestBagProp.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                                    if (Float.parseFloat(factorrightCard) < Float.parseFloat(factorforestBagProp)) {
                                        rightCard = forestBagProp;
                                    }
                                } else {
                                    rightCard = forestBagProp;
                                }
                            }
                            /*

                            String factorrightCard = rightCard.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                            String factorforestBagProp = forestBagProp.optJSONObject("propConfigVO").optJSONObject("detail").optString("factor");
                            long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                            long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                            Log.forest("factorrightCard:"+factorrightCard);
                            Log.forest("factorforestBagProp:"+factorforestBagProp);
                            Log.forest("recentExpireTimerightCard:"+recentExpireTimerightCard);
                            Log.forest("recentExpireTimeforestBagProp:"+recentExpireTimeforestBagProp);
                            //有在用倍卡，选择同倍率快到期卡
                            if (useFactor != null) {
                                if (Float.parseFloat(factorrightCard) == Float.parseFloat(factorforestBagProp)) {
                                    if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                        rightCard = forestBagProp;
                                    }
                                }
                            }
                            //没有在用倍卡，选择最高倍率卡
                            else {
                                if (rightCard != null) {
                                    if (Float.parseFloat(factorrightCard) < Float.parseFloat(factorforestBagProp)) {
                                        rightCard = forestBagProp;
                                    }
                                } else {
                                    rightCard = forestBagProp;
                                }
                            }*/
                    }
                }
            }
            return rightCard;
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
        return null;
    }

    private void usePropBeforeCollectEnergy(String userId) {
        if (Objects.equals(selfId, userId)) {
            return;
        }
        if (needDoubleClick()) {
            synchronized (usePropLockObj) {
                if (needDoubleClick()) {
                    useDoubleCard(getForestPropVOList());
                }
            }
        }
    }

    private Boolean needDoubleClick() {
        // 双击卡配置已停用（addField 被注释）→ 字段为 null，按"关闭"处理，避免 NPE
        if (doubleClickType == null || doubleClickType.getValue() == UsePropType.CLOSE) {
            return false;
        }
        Long doubleClickEndTime = usingProps.get(PropGroup.doubleClick.name());
        if (doubleClickEndTime == null) {
            return true;
        }
        return doubleClickEndTime < System.currentTimeMillis();
    }

    private void useDoubleCard(JSONArray forestPropVOList) {
        try {
            if (hasDoubleCardTime() && Status.canDoubleToday()) {
                // 背包查找 能量双击卡
                JSONObject jo = null;
                List<JSONObject> list = getPropGroup(forestPropVOList, PropGroup.doubleClick.name());
                if (!list.isEmpty()) {
                    jo = list.get(0);
                }
                if (jo == null || !jo.has("recentExpireTime")) {
                    // 配置已停用则字段为 null，按"关闭"处理
                    if (doubleCardConstant != null && doubleCardConstant.getValue()) {
                        // 商店兑换 限时能量双击卡
                        if (exchangeBenefit("SK20240805004754")) {
                            jo = getForestPropVO(getForestPropVOList(), "ENERGY_DOUBLE_CLICK_31DAYS");
                        } else if (exchangeBenefit("CR20230516000363")) {
                            jo = getForestPropVO(getForestPropVOList(), "LIMIT_TIME_ENERGY_DOUBLE_CLICK");
                        }
                    }
                }
                if (jo == null) {
                    return;
                }
                if (!jo.has("recentExpireTime")
                        && (doubleClickType == null || doubleClickType.getValue() == UsePropType.ONLY_LIMIT_TIME)) {
                    return;
                }
                // 使用能量双击卡
                if (consumeProp(jo)) {
                    Long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(jo.getJSONObject("propConfigVO").getLong("durationTime"));
                    usingProps.put(PropGroup.doubleClick.name(), endTime);
                    Status.DoubleToday();
                } else {
                    updateUsingPropsEndTime();
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
    }

    private boolean hasDoubleCardTime() {
        // 配置已停用（addField 被注释）→ 字段为 null，视为"不在使用时间范围"
        if (doubleCardTime == null) {
            return false;
        }
        long currentTimeMillis = System.currentTimeMillis();
        return TimeUtil.checkInTimeRange(currentTimeMillis, doubleCardTime.getValue());
    }

    /* 赠送道具 */
    private void giveProp() {
        Set<String> set = whoYouWantToGiveTo.getValue();
        if (set.isEmpty()) {
            return;
        }
        for (String userId : set) {
            if (UserIdMap.getCurrentUid() == null || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                continue;
            }
            giveProp(userId);
            break;
        }
    }

    private void giveProp(String targetUserId) {
        try {
            do {
                try {
                    JSONObject jo = new JSONObject(AntForestRpcCall.queryPropList(true));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    JSONArray forestPropVOList = jo.optJSONArray("forestPropVOList");
                    if (forestPropVOList != null && forestPropVOList.length() > 0) {
                        jo = forestPropVOList.getJSONObject(0);
                        String giveConfigId = jo.getJSONObject("giveConfigVO").getString("giveConfigId");
                        int holdsNum = jo.optInt("holdsNum", 0);
                        String propName = jo.getJSONObject("propConfigVO").getString("propName");
                        String propId = jo.getJSONArray("propIdList").getString(0);
                        jo = new JSONObject(AntForestRpcCall.giveProp(giveConfigId, propId, targetUserId));
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            Log.forest("赠送道具🎭[" + UserIdMap.getMaskName(targetUserId) + "]#" + propName);
                            if (holdsNum > 1 || forestPropVOList.length() > 1) {
                                continue;
                            }
                        }
                    }
                } finally {
                    TimeUtil.sleep(1500);
                }
                break;
            } while (true);
        } catch (Throwable th) {
            Log.err(TAG, "giveProp err:", th);
        }
    }

    /**
     * 绿色行动
     */
    private void ecoLife() {
        try {
            JSONObject jo = new JSONObject(EcoLifeRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.getJSONObject("data");
            if (!data.getBoolean("openStatus")) {
                Log.forest("绿色任务☘未开通");
                jo = new JSONObject(EcoLifeRpcCall.openEcolife());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                if (!String.valueOf(true).equals(JsonUtil.getValueByPath(jo, "data.opResult"))) {
                    return;
                }
                Log.forest("绿色任务🍀报告大人，开通成功(～￣▽￣)～可以愉快的玩耍了");
                jo = new JSONObject(EcoLifeRpcCall.queryHomePage());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                data = jo.getJSONObject("data");
            }
            String dayPoint = data.getString("dayPoint");
            JSONArray actionListVO = data.getJSONArray("actionListVO");
            if (ecoLifeOptions.getValue().contains("dish")) {
                photoGuangPan(dayPoint);
            }
            if (ecoLifeOptions.getValue().contains("tick")) {
                ecoLifeTick(actionListVO, dayPoint);
            }
        } catch (Throwable th) {
            Log.err(TAG, "ecoLife err:", th);
        }
    }

    /* 绿色行动打卡 */

    private void ecoLifeTick(JSONArray actionListVO, String dayPoint) {
        try {
            String source = "source";
            for (int i = 0; i < actionListVO.length(); i++) {
                JSONObject actionVO = actionListVO.getJSONObject(i);
                JSONArray actionItemList = actionVO.getJSONArray("actionItemList");
                for (int j = 0; j < actionItemList.length(); j++) {
                    JSONObject actionItem = actionItemList.getJSONObject(j);
                    if (!actionItem.has("actionId")) {
                        continue;
                    }
                    if (actionItem.getBoolean("actionStatus")) {
                        continue;
                    }
                    String actionId = actionItem.getString("actionId");
                    String actionName = actionItem.getString("actionName");
                    if ("photoguangpan".equals(actionId)) {
                        continue;
                    }
                    JSONObject jo = new JSONObject(EcoLifeRpcCall.tick(actionId, dayPoint, source));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.forest("绿色打卡🍀[" + actionName + "]");
                    }
                    TimeUtil.sleep(500);
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "ecoLifeTick err:", th);
        }
    }

    /**
     * 光盘行动
     */
    private void photoGuangPan(String dayPoint) {
        // if (!TaskCommon.IS_AFTER_6AM) {
        //    return;
        // }
        try {
            String source = "renwuGD";
            // 检查今日任务状态
            JSONObject jo = new JSONObject(EcoLifeRpcCall.queryDish(source, dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 更新光盘照片
            Map<String, String> dishImage = new HashMap<>();
            JSONObject data = jo.optJSONObject("data");
            if (data != null) {
                String beforeMealsImageUrl = data.optString("beforeMealsImageUrl");
                String afterMealsImageUrl = data.optString("afterMealsImageUrl");
                if (!StringUtil.isEmpty(beforeMealsImageUrl) && !StringUtil.isEmpty(afterMealsImageUrl)) {
                    Pattern pattern = Pattern.compile("img/(.*)/original");
                    Matcher beforeMatcher = pattern.matcher(beforeMealsImageUrl);
                    if (beforeMatcher.find()) {
                        dishImage.put("BEFORE_MEALS", beforeMatcher.group(1));
                    }
                    Matcher afterMatcher = pattern.matcher(afterMealsImageUrl);
                    if (afterMatcher.find()) {
                        dishImage.put("AFTER_MEALS", afterMatcher.group(1));
                    }
                    TokenConfig.saveDishImage(dishImage);
                }
            }
            if (Objects.equals("SUCCESS", jo.getJSONObject("data").getString("status"))) {
                return;
            }

            dishImage = TokenConfig.getRandomDishImage();
            if (dishImage == null) {
                Log.forest("光盘行动💿请先完成一次光盘打卡");
                return;
            }
            // 上传餐前照片
            jo = new JSONObject(EcoLifeRpcCall.uploadBeforeMealsDishImage(dishImage.get("BEFORE_MEALS"), dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 上传餐后照片
            jo = new JSONObject(EcoLifeRpcCall.uploadAfterMealsDishImage(dishImage.get("AFTER_MEALS"), dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 提交
            jo = new JSONObject(EcoLifeRpcCall.tick("photoguangpan", dayPoint, source));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String toastMsg = jo.getJSONObject("data").getString("toastMsg");
            Toast.show("光盘行动💿打卡完成#" + toastMsg);
            Log.forest("光盘行动💿打卡完成#" + toastMsg + "");
        } catch (Throwable t) {
            Log.err(TAG, "photoGuangPan err:", t);
        }
    }

    private void queryUserPatrol() {
        try {
            th:
            do {
                JSONObject jo = new JSONObject(AntForestRpcCall.queryUserPatrol());
                TimeUtil.sleep(500);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONObject resData = new JSONObject(AntForestRpcCall.queryMyPatrolRecord());
                TimeUtil.sleep(500);
                if (resData.optBoolean("canSwitch")) {
                    JSONArray records = resData.getJSONArray("records");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject record = records.getJSONObject(i);
                        JSONObject userPatrol = record.getJSONObject("userPatrol");
                        if (userPatrol.getInt("unreachedNodeCount") > 0) {
                            if ("silent".equals(userPatrol.getString("mode"))) {
                                JSONObject patrolConfig = record.getJSONObject("patrolConfig");
                                String patrolId = patrolConfig.getString("patrolId");
                                resData = new JSONObject(AntForestRpcCall.switchUserPatrol(patrolId));
                                TimeUtil.sleep(500);
                                if (MessageUtil.checkResultCode(TAG, resData)) {
                                    Log.forest("巡护⚖️-切换地图至" + patrolId);
                                }
                                continue th;
                            }
                            break;
                        }
                    }
                }

                JSONObject userPatrol = jo.getJSONObject("userPatrol");
                int currentNode = userPatrol.getInt("currentNode");
                String currentStatus = userPatrol.getString("currentStatus");
                int patrolId = userPatrol.getInt("patrolId");
                JSONObject chance = userPatrol.getJSONObject("chance");
                int leftChance = chance.getInt("leftChance");
                int leftStep = chance.getInt("leftStep");
                int usedStep = chance.getInt("usedStep");
                if ("STANDING".equals(currentStatus)) {
                    if (leftChance > 0) {
                        jo = new JSONObject(AntForestRpcCall.patrolGo(currentNode, patrolId));
                        TimeUtil.sleep(500);
                        patrolKeepGoing(jo.toString(), currentNode, patrolId);
                        continue;
                    } else if (leftStep >= 2000 && usedStep < 10000) {
                        jo = new JSONObject(AntForestRpcCall.exchangePatrolChance(leftStep));
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            int addedChance = jo.optInt("addedChance", 0);
                            Log.forest("步数兑换⚖️[巡护次数*" + addedChance + "]");
                            continue;
                        }
                    }
                } else if ("GOING".equals(currentStatus)) {
                    patrolKeepGoing(null, currentNode, patrolId);
                }
                break;
            } while (true);
        } catch (Throwable t) {
            Log.err(TAG, "queryUserPatrol err:", t);
        }
    }

    private void patrolKeepGoing(String s, int nodeIndex, int patrolId) {
        try {
            do {
                if (s == null) {
                    s = AntForestRpcCall.patrolKeepGoing(nodeIndex, patrolId, "image");
                }
                JSONObject jo = new JSONObject(s);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray jaEvents = jo.optJSONArray("events");
                if (jaEvents == null || jaEvents.length() == 0) {
                    return;
                }
                JSONObject userPatrol = jo.getJSONObject("userPatrol");
                int currentNode = userPatrol.getInt("currentNode");
                JSONObject events = jo.getJSONArray("events").getJSONObject(0);
                JSONObject rewardInfo = events.optJSONObject("rewardInfo");
                if (rewardInfo != null) {
                    JSONObject animalProp = rewardInfo.optJSONObject("animalProp");
                    if (animalProp != null) {
                        JSONObject animal = animalProp.optJSONObject("animal");
                        if (animal != null) {
                            Log.forest("巡护森林🏇🏻[" + animal.getString("name") + "碎片]");
                        }
                    }
                }
                if (!"GOING".equals(jo.getString("currentStatus"))) {
                    return;
                }
                JSONObject materialInfo = events.getJSONObject("materialInfo");
                String materialType = materialInfo.optString("materialType", "image");
                s = AntForestRpcCall.patrolKeepGoing(currentNode, patrolId, materialType);
                TimeUtil.sleep(100);
            } while (true);
        } catch (Throwable t) {
            Log.err(TAG, "patrolKeepGoing err:", t);
        }
    }

    /* 新版保护地：自动前进（领取首页机会 + 掷骰推进 + 事件确认） */
    private void monopolyPatrol() {
        try {
            JSONObject state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return;
            }
            JSONObject regionInfo = state.optJSONObject("regionInfo");
            JSONObject mapInfo = state.optJSONObject("mapInfo");
            JSONObject userInfo = state.optJSONObject("userInfo");
            if (regionInfo == null || mapInfo == null || userInfo == null) {
                Log.record("新版保护地🌲入口缺少区域、地图或用户状态，跳过本轮");
                return;
            }
            if (isMonopolyClosed(regionInfo) || isMonopolyClosed(mapInfo)) {
                Log.forest("新版保护地🌲当前区域或地图不在开放期，停止本轮");
                return;
            }
            String regionCode = regionInfo.optString("regionCode");
            boolean tasksEnabled = monopolyTasks.getValue();
            // 掷骰会解锁新任务，所以骰子用完后需要再补跑一次任务
            boolean tasksMayHaveChanged = false;
            if (tasksEnabled) {
                monopolyTask(regionCode);
            }
            // 首次进入地图时服务端要求带引导参数掷骰
            boolean guideRoll = userInfo.optBoolean("firstEnterMonopoly");
            JSONObject pendingEvent = state.optJSONObject("eventInfo");

            // 首页道具：领取当日巡护机会（骰子）
            JSONObject props = new JSONObject(AntForestRpcCall.triggerMonopolyHomeProps());
            if (!MessageUtil.checkResultCode(TAG, props)) {
                return;
            }
            state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return;
            }
            if (pendingEvent == null) {
                pendingEvent = state.optJSONObject("eventInfo");
            }

            Set<String> confirmedEvents = new HashSet<>();
            while (!Thread.currentThread().isInterrupted()) {
                JSONObject event = pendingEvent;
                if (event != null && event.optBoolean("needConfirm")) {
                    String eventId = event.optString("eventId");
                    String eventType = event.optString("eventType");
                    String actionKey = "CHARITY".equals(eventType) ? "confirm" : ("SPECIAL".equals(eventType) ? "skip" : null);
                    JSONObject displayInfo = event.optJSONObject("displayInfo");
                    if (eventId.isEmpty() || actionKey == null || displayInfo == null
                            || !"SINGLE_ACTION_CONFIRM".equals(displayInfo.optString("flowType"))) {
                        Log.record("新版保护地🌲事件缺少可执行决策，保留当前事件[" + eventId + "]");
                        return;
                    }
                    if (confirmedEvents.contains(eventId)) {
                        Log.record("新版保护地🌲事件[" + eventId + "]已提交确认，等待后续调度刷新");
                        return;
                    }
                    JSONObject confirmation = new JSONObject(AntForestRpcCall.confirmMonopolyEvent(eventId, actionKey));
                    if (!MessageUtil.checkResultCode(TAG, confirmation)) {
                        return;
                    }
                    confirmedEvents.add(eventId);
                    Log.forest("新版保护地🌲事件[" + eventId + "]已确认");
                    state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                    if (!MessageUtil.checkResultCode(TAG, state)) {
                        return;
                    }
                    pendingEvent = state.optJSONObject("eventInfo");
                    continue;
                }

                int diceCount = state.optInt("totalDiceCount", -1);
                if (diceCount < 0) {
                    Log.record("新版保护地🌲响应缺少可用骰子数量，跳过本轮");
                    return;
                }
                if (diceCount == 0) {
                    if (tasksEnabled && tasksMayHaveChanged) {
                        monopolyTask(regionCode);
                        tasksMayHaveChanged = false;
                        state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                        if (!MessageUtil.checkResultCode(TAG, state)) {
                            return;
                        }
                        pendingEvent = state.optJSONObject("eventInfo");
                        continue;
                    }
                    Log.record("新版保护地🌲当前无可用骰子，后续调度继续查询");
                    return;
                }

                JSONObject currentUserInfo = state.optJSONObject("userInfo");
                int previousSteps = currentUserInfo == null ? -1 : currentUserInfo.optInt("stepCount", -1);
                JSONObject roll = new JSONObject(AntForestRpcCall.rollMonopolyDice(guideRoll));
                if (!MessageUtil.checkResultCode(TAG, roll)) {
                    return;
                }
                guideRoll = false;
                pendingEvent = roll.optJSONObject("eventInfo");
                JSONObject rollUserInfo = roll.optJSONObject("userInfo");
                int steps = rollUserInfo == null ? -1 : rollUserInfo.optInt("stepCount", -1);
                // 骰子数、步数、事件三者都没变化，说明服务端没受理，留到下次调度重试
                if (roll.optInt("totalDiceCount", -1) == diceCount && steps == previousSteps && pendingEvent == null) {
                    Log.record("新版保护地🌲掷骰后未确认状态变化，保留后续调度");
                    return;
                }
                tasksMayHaveChanged = true;
                Log.forest("新版保护地🌲掷骰[" + roll.optInt("diceNumber") + "]剩余" + roll.optInt("totalDiceCount") + "次");
                TimeUtil.sleep(500);
                state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                if (!MessageUtil.checkResultCode(TAG, state)) {
                    return;
                }
                if (pendingEvent == null) {
                    pendingEvent = state.optJSONObject("eventInfo");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "monopolyPatrol err:", t);
        }
    }

    /* 新版保护地：区域/地图是否未开放或已结束（缺少日期字段视为一直开放） */
    private boolean isMonopolyClosed(JSONObject activity) {
        long now = System.currentTimeMillis();
        long start = parseMonopolyDate(activity.optString("startDate"));
        long end = parseMonopolyDate(activity.optString("endDate"));
        return (start > 0 && now < start) || (end > 0 && now >= end);
    }

    /* 解析新版保护地的 "yyyy-MM-dd HH:mm:ss" 时间串（东八区），解析失败返回 0 */
    private long parseMonopolyDate(String date) {
        if (StringUtil.isEmpty(date)) {
            return 0L;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            format.setLenient(false);
            Date parsed = format.parse(date);
            return parsed == null ? 0L : parsed.getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    /* 新版保护地部分接口的响应会被包在 resData 里 */
    private JSONObject monopolyResponse(String raw) throws JSONException {
        JSONObject jo = new JSONObject(raw);
        JSONObject resData = jo.optJSONObject("resData");
        return resData == null ? jo : resData;
    }

    /** 当前保护地区域码：由 entryInfo 动态下发，区域轮换无需改代码；取不到返回空串 */
    private String getMonopolyRegionCode() {
        try {
            JSONObject state = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return "";
            }
            JSONObject regionInfo = state.optJSONObject("regionInfo");
            return regionInfo == null ? "" : regionInfo.optString("regionCode");
        } catch (Throwable t) {
            Log.err(TAG, "getMonopolyRegionCode err:", t);
            return "";
        }
    }

    /* 新版保护地：完成可自动执行的任务并领取奖励 */
    private boolean monopolyTask(String regionCode) {
        // sceneCode 为跨保护地区域固定的任务场景标识；regionCode 由 entryInfo 动态下发(区域轮换无需改代码)。
        // 服务端按 regionCode 返回该区域任务,请求带固定 sceneCode 即可,拿不到任务时按空列表跳过。
        String sceneCode = MONOPOLY_TASK_SCENE_HSDWY;
        boolean changed = false;
        try {
            // 每轮只处理一个任务，最多 20 轮：服务端状态不变时靠轮次上限收敛
            for (int round = 0; round < 20; round++) {
                JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(regionCode, sceneCode));
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    return changed;
                }
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList == null) {
                    Log.record("新版保护地🌲任务列表为空，跳过");
                    return changed;
                }
                boolean acted = false;
                for (int i = 0; i < taskInfoList.length() && !acted; i++) {
                    JSONObject task = taskInfoList.optJSONObject(i);
                    JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                    // 用任务自带的真实 sceneCode 做完成/领奖(服务端按 regionCode 返回,sceneCode 以任务为准)
                    String taskSceneCode = base == null ? "" : base.optString("sceneCode");
                    if (taskSceneCode.isEmpty()) {
                        continue;
                    }
                    String taskType = base.optString("taskType");
                    if (taskType.isEmpty()) {
                        continue;
                    }
                    String title = monopolyTaskTitle(base, taskType);
                    String status = base.optString("taskStatus");
                    // 黑名单任务静默跳过；已完成待领奖的仍把奖励领掉（与森林任务一致）
                    if (isMonopolyTaskBlacklisted(title) && !"FINISHED".equals(status)) {
                        continue;
                    }
                    if ("FINISHED".equals(status)) {
                        acted = true;
                        changed |= monopolyReceiveTaskAward(regionCode, taskType, taskSceneCode, title);
                    } else if ("TODO".equals(status) && "NORMAL".equals(base.optString("taskMode"))
                            && "VISIT_FLOAT_BALL".equals(base.optString("taskProdPlayType"))) {
                        // 只自动做"浏览浮球"这类纯等待任务，其余需要真实业务动作的任务不碰
                        acted = true;
                        changed |= monopolyFinishTask(base, taskType, taskSceneCode, title);
                    }
                }
                if (!acted) {
                    return changed;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "monopolyTask err:", t);
        }
        return changed;
    }

    /* 新版保护地任务展示名：bizInfo.title → bizInfo.taskTitle → 任务类型（各接口键名不统一） */
    private String monopolyTaskTitle(JSONObject base, String taskType) {
        String bizInfo = base.optString("bizInfo");
        if (!bizInfo.isEmpty()) {
            try {
                JSONObject biz = new JSONObject(bizInfo);
                String title = biz.optString("title");
                if (title.isEmpty()) {
                    title = biz.optString("taskTitle");
                }
                if (!title.isEmpty()) {
                    return title;
                }
            } catch (Throwable t) {
                Log.err(TAG, "monopolyTaskTitle err:", t);
            }
        }
        return taskType;
    }

    /* 新版保护地任务是否在配置的黑名单里（键统一剥掉标题末尾的 "(n/N)" 次数后缀） */
    private boolean isMonopolyTaskBlacklisted(String title) {
        Set<String> blackList = MonopolyTaskList.getValue();
        return blackList != null && blackList.contains(blackTaskKey(title));
    }

    /* 失败响应按统一判据自动拉黑（可重试错误不会拉黑），受「自动黑名单」开关控制 */
    private void markMonopolyTaskBlackList(String title, JSONObject jo) {
        if (Boolean.TRUE.equals(AutoMonopolyTaskList.getValue())) {
            MessageUtil.checkResultCodeAndMarkTaskBlackList("MonopolyTaskList", blackTaskKey(title), jo);
        }
    }

    /* 新版保护地：按服务端下发的时长等待后提交任务完成 */
    private boolean monopolyFinishTask(JSONObject base, String taskType, String sceneCode, String title) {
        try {
            long seconds = 0L;
            String prodPlayParam = base.optString("prodPlayParam");
            if (!prodPlayParam.isEmpty()) {
                seconds = new JSONObject(prodPlayParam).optLong("timeCount", 0L);
            }
            if (seconds <= 0 || seconds > MONOPOLY_MAX_TASK_SECONDS) {
                Log.record("新版保护地🌲任务[" + title + "]下发时长异常(" + seconds + "s)，跳过");
                return false;
            }
            // 底线：硬交易/履约/充值类只能靠真实交易完成，伪申报会被服务端判风险操作（回 1009），
            // 一律不发、一次即永久拉黑（与森林乐园限定任务同一口径，判据见 isTransactionTaskHard）。
            // 放在 sleep 之前：这类任务本就不做，没必要空等下发的时长（最多 MONOPOLY_MAX_TASK_SECONDS）。
            // 软判据（_cz 数字等疑似充值）不在此拦：部分实测可通过伪申报完成，失败交下面的自动黑名单兜底。
            if (TaskAlternative.isTransactionTaskHard(taskType)) {
                MessageUtil.MarkTaskBlackListPermanent("AntForestV2", "MonopolyTaskList", "新版保护地任务",
                        blackTaskKey(title));
                Log.forest("新版保护地🌲⏭️交易/履约类[" + title + "]#不申报，已永久拉黑");
                return false;
            }
            TimeUtil.sleep(TimeUnit.SECONDS.toMillis(seconds));
            JSONObject jo = monopolyResponse(AntForestRpcCall.finishMonopolyTask(taskType, sceneCode));
            markMonopolyTaskBlackList(title, jo);
            // 不支持 rpc 调用（400000040）→ 换另一种实现方案（doFarmTask 伪申报）；
            // 响应不可信，成败仍以任务列表为准（下一轮列表 FINISHED 即去领奖）
            if (TaskAlternative.hit(jo, sceneCode)) {
                TaskAlternative.trigger(null, null, title, taskType, sceneCode, "新版保护地🌲",
                        msg -> Log.forest(msg));
                return true;
            }
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("新版保护地🌲任务完成[" + title + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "monopolyFinishTask err:", t);
        }
        return false;
    }

    /* 新版保护地：领取任务奖励 */
    private boolean monopolyReceiveTaskAward(String regionCode, String taskType, String sceneCode, String title) {
        try {
            // 领奖响应没有明确的能量到账量字段，只能用账户余额差值估算真实到账量
            int energyBefore = queryCurrentEnergy();
            JSONObject jo = monopolyResponse(AntForestRpcCall.receiveMonopolyTask(taskType, sceneCode));
            boolean ok = MessageUtil.checkSuccess(TAG, jo);
            // 领奖收口：动作响应不可信——领奖失败时先按任务列表复核"已领到"（RECEIVED / 任务已消失），
            // 未确认才交自动拉黑。顺序由 TaskAward 固定，调用点写不反（写反会误拉黑真任务）。
            if (!ok && TaskAward.confirmReceivedOrBlackList("新版保护地🌲任务奖励",
                    k -> probeMonopolyStatus(regionCode, taskType, sceneCode), taskType, title,
                    () -> markMonopolyTaskBlackList(title, jo), msg -> Log.forest(msg))) {
                recordMonopolyAward(title, energyBefore);
                return true;
            }
            if (ok) {
                markMonopolyTaskBlackList(title, jo);
                Log.forest("新版保护地🌲任务奖励[" + title + "]");
                recordMonopolyAward(title, energyBefore);
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "monopolyReceiveTaskAward err:", t);
        }
        return false;
    }

    /** 保护地任务探针：重拉当前区域任务列表，按 taskType 读该任务当前状态（只读，领奖复核用） */
    private TaskAttemptPolicy.ProbeResult probeMonopolyStatus(String regionCode, String taskType, String sceneCode) {
        try {
            JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(regionCode, sceneCode));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
            if (taskInfoList == null) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            for (int i = 0; i < taskInfoList.length(); i++) {
                JSONObject task = taskInfoList.optJSONObject(i);
                JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                if (base == null || !taskType.equals(base.optString("taskType"))) {
                    continue;
                }
                // 状态字符串到探针结果的映射统一在 ProbeResult.of（服务端新增状态时只改那里）
                return TaskAttemptPolicy.ProbeResult.of(base.optString("taskStatus"));
            }
            // 任务已从列表消失：视为已完成且已领（避免重复领奖把统计刷高）
            return TaskAttemptPolicy.ProbeResult.GONE;
        } catch (Throwable t) {
            Log.err(TAG, "probeMonopolyStatus err:", t);
            return TaskAttemptPolicy.ProbeResult.UNKNOWN;
        }
    }

    /**
     * 领取保护地奖励后按账户能量余额差值记账。
     *
     * <p>保护地领奖响应没有可直接读取的到账量字段（不同于森林集市的 {@code receivedEnergyAmount} /
     * 绿色租赁的 {@code zulinshangpinliulan}），因此以领奖前后 {@code currentEnergy} 的差值作为
     * 实际到账量。差值需为正才计入，避免服务端并发扣减导致负数误记。
     *
     * @param energyBefore 领奖前查询到的能量余额；{@code < 0} 表示查询失败，放弃记账
     */
    private void recordMonopolyAward(String title, int energyBefore) {
        if (energyBefore < 0) {
            return;
        }
        int energyAfter = queryCurrentEnergy();
        if (energyAfter < 0) {
            return;
        }
        int gained = energyAfter - energyBefore;
        if (gained <= 0) {
            return;
        }
        Log.forest("新版保护地🌲任务奖励[" + title + "]到账[" + gained + "g能量]");
        totalCollected += gained;
        Statistics.addData(Statistics.DataType.COLLECTED, gained);
    }

    /**
     * 查询账户当前能量余额（{@code userBaseInfo.currentEnergy}）。
     *
     * @return 余额；查询失败返回 {@code -1}
     */
    private int queryCurrentEnergy() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return -1;
            }
            JSONObject userBaseInfo = jo.optJSONObject("userBaseInfo");
            if (userBaseInfo == null || !userBaseInfo.has("currentEnergy")) {
                return -1;
            }
            return userBaseInfo.optInt("currentEnergy", -1);
        } catch (Throwable t) {
            Log.err(TAG, "queryCurrentEnergy err:", t);
            return -1;
        }
    }

    /* 新版动物伙伴：领取已产生的派遣能量 */
    private void collectMonopolyAnimalEnergy() {
        try {
            String uid = UserIdMap.getCurrentUid();
            if (StringUtil.isEmpty(uid)) {
                return;
            }
            JSONObject jo = monopolyResponse(AntForestRpcCall.queryUsingCreatureInfo(uid));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject creature = jo.optJSONObject("userCreatureVO");
            if (creature == null) {
                return;
            }
            JSONObject energy = creature.optJSONObject("robEnergyVO");
            if (energy == null) {
                Log.record("新版动物伙伴🦩缺少能量状态，跳过");
                return;
            }
            if (energy.optBoolean("energyIsCollect")) {
                return;
            }
            if (energy.optInt("yesterdayRobEnergy", 0) <= 0) {
                // 昨日没有产出能量，属于正常情况，不打扰用户
                return;
            }
            String creatureCode = creature.optString("creatureCode");
            String shortDay = energy.optString("yesterdayShortDay");
            if (!energy.has("energyIsCollect") || creatureCode.isEmpty() || shortDay.isEmpty()) {
                Log.record("新版动物伙伴🦩能量缺少领取标识，跳过");
                return;
            }
            JSONObject collect = monopolyResponse(AntForestRpcCall.collectMonopolyCreatureEnergy(creatureCode, shortDay));
            if (!MessageUtil.checkResultCode(TAG, collect)) {
                return;
            }
            int collected = collect.optInt("collectedEnergy", -1);
            if (collected < 0) {
                Log.record("新版动物伙伴🦩领取成功但缺少实际到账量，不计入统计");
                return;
            }
            if (collected > 0) {
                Statistics.addData(Statistics.DataType.COLLECTED, collected);
            }
            Log.forest("新版动物伙伴🦩[" + creature.optString("creatureName", creatureCode) + "]派遣能量[" + collected + "g]");
        } catch (Throwable t) {
            Log.err(TAG, "collectMonopolyAnimalEnergy err:", t);
        }
    }

    /* 新版动物伙伴：自动派遣（已有动物在巡护时不替换） */
    private boolean dispatchMonopolyAnimal(boolean canConsumeAnimalProp) {
        try {
            if (!canConsumeAnimalProp) {
                return false;
            }
            int consumePropType = consumeAnimalPropType.getValue();
            if (consumePropType == ConsumeAnimalPropType.NONE) {
                Log.record("新版动物伙伴🦩未开启派遣动物伙伴，跳过");
                return false;
            }
            JSONObject jo = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            if (jo.optBoolean("usingMonopolyCreature")) {
                Log.record("新版动物伙伴🦩已有动物在巡护，保留当前伙伴");
                return true;
            }
            JSONArray creatureList = jo.optJSONArray("creatureList");
            if (creatureList == null) {
                Log.record("新版动物伙伴🦩入口缺少动物列表，跳过");
                return false;
            }
            List<JSONObject> candidates = new ArrayList<>();
            boolean allHasInitialRobEnergy = true;
            for (int i = 0; i < creatureList.length(); i++) {
                JSONObject creature = creatureList.optJSONObject(i);
                if (creature == null || !isMonopolyCreatureIdle(creature)) {
                    continue;
                }
                candidates.add(creature);
                if (creature.isNull("initialRobEnergy")) {
                    allHasInitialRobEnergy = false;
                }
            }
            if (candidates.isEmpty()) {
                Log.record("新版动物伙伴🦩当前没有可派遣的动物");
                return false;
            }
            // 按"派遣动物伙伴"配置选人：默认顺序取第一个；按最多能量派遣时新版按预计产出最高选
            JSONObject selected = candidates.get(0);
            if (consumePropType == ConsumeAnimalPropType.QUANTITY && allHasInitialRobEnergy) {
                for (JSONObject candidate : candidates) {
                    if (candidate.optInt("initialRobEnergy") > selected.optInt("initialRobEnergy")) {
                        selected = candidate;
                    }
                }
            }
            String creatureCode = selected.getString("creatureCode");
            JSONObject assigned = new JSONObject(AntForestRpcCall.assignMonopolyCreature(creatureCode));
            if (MessageUtil.checkResultCode(TAG, assigned)) {
                Log.forest("新版动物伙伴🦩派遣[" + selected.optString("creatureName", creatureCode) + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "dispatchMonopolyAnimal err:", t);
        }
        return false;
    }

    /* 新版动物是否空闲可派遣 */
    private boolean isMonopolyCreatureIdle(JSONObject creature) {
        if (StringUtil.isEmpty(creature.optString("creatureCode"))) {
            return false;
        }
        if ("using".equals(creature.optString("status")) || creature.optLong("assignTime", 0L) > 0) {
            return false;
        }
        JSONObject energy = creature.optJSONObject("robEnergyVO");
        if (energy == null) {
            return true;
        }
        if (energy.has("robRemainDays") && energy.optInt("robRemainDays") <= 0) {
            return false;
        }
        int maxWorkDays = energy.optInt("maxWorkDays", 0);
        return maxWorkDays <= 0 || energy.optInt("alreadyWorkDays", 0) < maxWorkDays;
    }

    // 查询可派遣伙伴（分页取全，返回是否派遣成功）
    private boolean queryAnimalPropList() {
        try {
            boolean sequence = consumeAnimalPropType.getValue() == ConsumeAnimalPropType.SEQUENCE;
            JSONObject animalProp = null;
            String nextCursor = null;
            for (int page = 0; page < 20; page++) {
                JSONObject jo = new JSONObject(AntForestRpcCall.queryAnimalPropList(nextCursor));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                JSONArray animalProps = jo.optJSONArray("animalProps");
                if (animalProps != null) {
                    for (int i = 0; i < animalProps.length(); i++) {
                        JSONObject prop = animalProps.optJSONObject(i);
                        if (prop == null) {
                            continue;
                        }
                        if (animalProp == null) {
                            animalProp = prop;
                        } else if (!sequence && animalPropEnergy(prop) > animalPropEnergy(animalProp)) {
                            animalProp = prop;
                        }
                    }
                }
                if (sequence || !jo.optBoolean("hasMore")) {
                    break;
                }
                String cursor = jo.optString("nextCursor");
                if (StringUtil.isEmpty(cursor) || cursor.equals(nextCursor)) {
                    break;
                }
                nextCursor = cursor;
            }
            return consumeAnimalProp(animalProp);
        } catch (Throwable t) {
            Log.err(TAG, "queryAnimalPropList err:", t);
        }
        return false;
    }

    // 动物道具每日可获能量（"按最多能量派遣"按此最高选择，字段取自 partner.robAbility.robEnergyInDaily）
    private int animalPropEnergy(JSONObject animalProp) {
        JSONObject partner = animalProp.optJSONObject("partner");
        if (partner == null) {
            return 0;
        }
        JSONObject robAbility = partner.optJSONObject("robAbility");
        return robAbility == null ? 0 : robAbility.optInt("robEnergyInDaily", 0);
    }

    // 派遣伙伴，返回是否派遣成功
    private boolean consumeAnimalProp(JSONObject animalProp) {
        if (animalProp == null) {
            return false;
        }
        try {
            String propGroup = animalProp.getJSONObject("main").getString("propGroup");
            String propType = animalProp.getJSONObject("main").getString("propType");
            String name = animalProp.getJSONObject("partner").getString("name");
            JSONObject jo = new JSONObject(AntForestRpcCall.consumeProp(propGroup, propType, false));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("巡护派遣🐆[" + name + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "consumeAnimalProp err:", t);
        }
        return false;
    }

    private int forFriendCollectEnergy(String targetUserId, long bubbleId) {
        int helped = 0;
        try {
            String s = AntForestRpcCall.forFriendCollectEnergy(targetUserId, bubbleId);
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray jaBubbles = jo.getJSONArray("bubbles");
                for (int i = 0; i < jaBubbles.length(); i++) {
                    jo = jaBubbles.getJSONObject(i);
                    helped += jo.getInt("collectedEnergy");
                }
                if (helped > 0) {
                    Log.forest("帮收能量🧺[" + UserIdMap.getMaskName(targetUserId) + "]#" + helped + "g");
                    totalHelpCollected += helped;
                    Statistics.addData(Statistics.DataType.HELPED, helped);
                } else {
                    Log.record("帮[" + UserIdMap.getMaskName(targetUserId) + "]收取失败");
                    Log.i("，UserID：" + targetUserId + "，BubbleId" + bubbleId);
                }
            } else {
                Log.record("[" + UserIdMap.getMaskName(targetUserId) + "]" + jo.getString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "forFriendCollectEnergy err:", t);
        }
        return helped;
    }

    public static JSONArray getForestPropVOList() {
        JSONArray forestPropVOList = new JSONArray();
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryPropList(false));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                forestPropVOList = jo.getJSONArray("forestPropVOList");
            }
        } catch (Throwable th) {
            Log.err(TAG, "getForestPropVOList err:", th);
        }
        return forestPropVOList;
    }

    // 获取道具组全部道具
    public static List<JSONObject> getPropGroup(JSONArray forestPropVOList, String propGroup) {
        List<JSONObject> list = new ArrayList<>();
        try {
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestPropVO = forestPropVOList.getJSONObject(i);
                if (forestPropVO.getString("propGroup").equals(propGroup)) {
                    list.add(forestPropVO);
                }
            }
            Collections.sort(list, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject jsonObject1, JSONObject jsonObject2) {
                    try {
                        int durationTime1 = jsonObject1.getJSONObject("propConfigVO").getInt("durationTime");
                        int durationTime2 = jsonObject2.getJSONObject("propConfigVO").getInt("durationTime");
                        boolean hasExpireTime1 = jsonObject1.has("recentExpireTime");
                        boolean hasExpireTime2 = jsonObject2.has("recentExpireTime");
                        if (hasExpireTime1 && hasExpireTime2) {
                            long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationTime1);
                            long recentExpireTime = jsonObject2.getLong("recentExpireTime");
                            if (endTime < recentExpireTime) {
                                return -1;
                            } else {
                                return durationTime2 - durationTime1;
                            }
                        } else if (!hasExpireTime1 && !hasExpireTime2) {
                            return durationTime1 - durationTime2;
                        } else {
                            return hasExpireTime1 ? -1 : 1;
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (Throwable th) {
            Log.err(TAG, "getPropGroup err:", th);
        }
        return list;
    }

    /*
     * 查找背包道具
     * prop
     * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
     */
    private JSONObject getForestPropVO(JSONArray forestPropVOList, String propType) {
        try {
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestPropVO = forestPropVOList.getJSONObject(i);
                if (forestPropVO.getString("propType").equals(propType)) {
                    return forestPropVO;
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "getForestPropVO err:", th);
        }
        return null;
    }

    /*
     * 使用背包道具
     * prop
     * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
     */
    public static Boolean consumeProp(JSONObject prop) {
        try {
            // 使用道具
            String propId = prop.getJSONArray("propIdList").getString(0);
            String propType = prop.getString("propType");
            String propGroup = prop.getString("propGroup");
            String propName = prop.getJSONObject("propConfigVO").getString("propName");
            return consumeProp(propGroup, propId, propType, propName);
        } catch (Throwable th) {
            Log.err(TAG, "consumeProp err:", th);
        }
        return false;
    }

    private static Boolean consumeProp(String propGroup, String propId, String propType, String propName) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.consumeProp(propGroup, propId, propType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("使用道具🎭[" + propName + "]");
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "consumeProp err:", th);
        }
        return false;
    }

    /** 活力值商店的分类：官方按这 5 个 labelType 分别拉取，少查一个列表就不全 */
    private static final String[] VITALITY_LABEL_TYPES = {"", "SC_ASSETS", "SKIN", "JEWELRY", "OTHER"};

    /** 单分类翻页上限：hasMore 异常一直为真时兜底 */
    private static final int VITALITY_ITEM_MAX_PAGES = 10;

    /** 活力兑换遇「系统繁忙」的当日重试上限：临时故障按次计数，达到上限才当日停（不再一次繁忙就封死当天） */
    private static final int MAX_VITALITY_EXCHANGE_BUSY = 3;

    /**
     * 获取活力值商店所有商品信息。
     * <p>原先只查 SC_ASSETS 的第一页 ⇒ 权益列表只有一小部分；改为按官方实测的 5 个 labelType
     * 分别拉取，并用响应里的 hasMore 翻页。
     */
    private void getAllSkuInfo() {
        try {
            int got = 0;
            for (String labelType : VITALITY_LABEL_TYPES) {
                int cnt = 0;
                for (int page = 0; page < VITALITY_ITEM_MAX_PAGES; page++) {
                    JSONObject jo = new JSONObject(AntForestRpcCall.itemList(labelType, page * AntForestRpcCall.VITALITY_ITEM_PAGE_SIZE));
                    if (!MessageUtil.checkSuccess(TAG, jo)) {
                        break;
                    }
                    JSONArray itemInfoVOList = optItemInfoVOList(jo);
                    if (itemInfoVOList == null || itemInfoVOList.length() == 0) {
                        break;
                    }
                    for (int i = 0; i < itemInfoVOList.length(); i++) {
                        getSkuInfoByItemInfoVO(itemInfoVOList.getJSONObject(i));
                        cnt++;
                    }
                    if (!hasMore(jo)) {
                        break;
                    }
                }
                got += cnt;
                Log.i("活力值商店列表：[" + (labelType.isEmpty() ? "全部" : labelType) + "]取到" + cnt + "条");
            }
            VitalityBenefitIdMap.save(UserIdMap.getCurrentUid());
            Log.i("活力值商店列表：共取" + got + "条，清单共" + VitalityBenefitIdMap.getMap().size() + "条");
        } catch (Throwable th) {
            Log.err(TAG, "getAllSkuInfo err:", th);
        }
    }

    /** 商品列表可能在顶层、也可能在 resData 下（抓包两层都出现过），两层都取 */
    private static JSONArray optItemInfoVOList(JSONObject jo) {
        JSONArray list = jo.optJSONArray("itemInfoVOList");
        if (list == null) {
            JSONObject resData = jo.optJSONObject("resData");
            if (resData != null) {
                list = resData.optJSONArray("itemInfoVOList");
            }
        }
        return list;
    }

    /** hasMore 表示还有下一页，同样两层都取 */
    private static boolean hasMore(JSONObject jo) {
        if (jo.has("hasMore")) {
            return jo.optBoolean("hasMore", false);
        }
        JSONObject resData = jo.optJSONObject("resData");
        return resData != null && resData.optBoolean("hasMore", false);
    }

    private void getSkuInfoBySpuId(String spuId) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.itemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject spuItemInfoVo = jo.getJSONObject("spuItemInfoVO");
            getSkuInfoByItemInfoVO(spuItemInfoVo);
            VitalityBenefitIdMap.save(UserIdMap.getCurrentUid());
        } catch (Throwable th) {
            Log.err(TAG, "getSkuInfoBySpuId err:", th);
        }
    }

    private void getSkuInfoByItemInfoVO(JSONObject spuItem) {
        try {
            String spuId = spuItem.getString("spuId");
            JSONArray skuModelList = spuItem.getJSONArray("skuModelList");
            for (int i = 0; i < skuModelList.length(); i++) {
                JSONObject skuModel = skuModelList.getJSONObject(i);
                String skuId = skuModel.getString("skuId");
                String skuName = skuModel.getString("skuName");
                if (!skuModel.has("spuId")) {
                    skuModel.put("spuId", spuId);
                }
                skuInfo.put(skuId, skuModel);
                VitalityBenefitIdMap.add(skuId, skuName);
            }
            VitalityBenefitIdMap.save(UserIdMap.getCurrentUid());
        } catch (Throwable th) {
            Log.err(TAG, "getSkuInfoByItemInfoVO err:", th);
        }
    }

    /*
     * 兑换活力值商品
     * sku
     * spuId, skuId, skuName, exchangedCount, price[amount]
     * exchangedCount == 0......
     */
    private Boolean exchangeBenefit(String skuId) {
        if (skuInfo.isEmpty()) {
            getAllSkuInfo();
        }
        JSONObject sku = skuInfo.get(skuId);
        if (sku == null) {
            Log.record("活力兑换🎐找不到要兑换的权益！");
            return false;
        }
        try {
            String skuName = sku.getString("skuName");
            JSONArray itemStatusList = sku.getJSONArray("itemStatusList");
            for (int i = 0; i < itemStatusList.length(); i++) {
                String itemStatus = itemStatusList.getString(i);
                if (ItemStatus.REACH_LIMIT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_POINT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_STOCK.name().equals(itemStatus)) {
                    Log.record("活力兑换🎐[" + skuName + "]停止:" + ItemStatus.valueOf(itemStatus).nickName());
                    if (ItemStatus.REACH_LIMIT.name().equals(itemStatus)) {
                        Status.flagToday("forest::exchangeLimit::" + skuId);
                    }
                    return false;
                }
            }
            String spuId = sku.getString("spuId");
            if (exchangeBenefit(spuId, skuId, skuName)) {
                return true;
            }
            getSkuInfoBySpuId(spuId);
        } catch (Throwable th) {
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }

    public static Boolean exchangeBenefit(String spuId, String skuId, String skuName) {
        try {
            if (exchangeBenefit(spuId, skuId)) {
                Status.vitalityExchangeBenefitToday(skuId);
                int exchangedCount = Status.getVitalityExchangeBenefitCountToday(skuId);
                Log.forest("活力兑换🎐[" + skuName + "]#第" + exchangedCount + "次");
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }

    private static Boolean exchangeBenefit(String spuId, String skuId) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.exchangeBenefit(spuId, skuId));
            if (jo.has("errorMessage")) {
                String errorMessage = jo.optString("errorMessage");
                //「系统繁忙」是临时故障：按次计数，达到当日上限才落"今日停止"标记；
                // 不再一次繁忙就把当天封死，下一轮任务仍会继续尝试
                if (errorMessage.equals("系统繁忙，请稍后再试。")) {
                    int busyCount = Status.getIntFlagToday("forest::exchangeBusy::" + skuId) + 1;
                    if (busyCount >= MAX_VITALITY_EXCHANGE_BUSY) {
                        Status.flagToday("forest::exchangeLimit::" + skuId);
                        Log.record("活力兑换🎐[" + skuId + "]系统繁忙已达当日上限(" + busyCount + "次)，今日停止");
                    } else {
                        Status.setIntFlagToday("forest::exchangeBusy::" + skuId, busyCount);
                        Log.record("活力兑换🎐[" + skuId + "]系统繁忙(第" + busyCount + "次)，暂不落标记");
                    }
                }
            }
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }

    private void teamCooperateWater() {
        // 记录本次是否由本方法切到组队模式，只要是就得切回来
        boolean switchedToTeam = false;
        try {
            int userDailyTarget = Math.min(Math.max(partnerteamWaterNum.getValue(), 10), 5000);
            int todayUsed = Status.getforestHuntHelpToday("FLAG_TEAM_WATER_DAILY_COUNT");
            int userRemainingQuota = userDailyTarget - todayUsed;

            if (userRemainingQuota < 10) {
                Log.record("组队合种今日已达标 (已浇" + todayUsed + "g / 目标" + userDailyTarget + "g)，跳过");
                return;
            }

            JSONObject homeJo = queryTeamHomePage();
            if (homeJo == null) {
                return;
            }

            // 必须先把模式切到组队，个人模式的返回体里没有 teamHomeResult，取不到 teamId
            int state = teamState(homeJo);
            if (state < 0) {
                // 无法判定组队状态（字段缺失/异常）：不主动改账户设置，直接跳过
                Log.record("无法判定组队状态，跳过组队合种浇水");
                return;
            }
            if (state == 0) {
                Log.record("不在队伍模式,已为您切换至组队浇水");
                if (!updateUserConfiginTeam(true)) {
                    Log.record("切换到组队模式失败，跳过组队合种浇水");
                    return;
                }
                switchedToTeam = true;
                Status.flagToday(FLAG_TEAM_MODE_SWITCHED);
                homeJo = queryTeamHomePage();
                if (homeJo == null) {
                    return;
                }
            }

            String teamId = getTeamId(homeJo);
            if (teamId.isEmpty()) {
                Log.record("未获取到组队合种 TeamID");
                return;
            }

            JSONObject userBaseInfo = homeJo.optJSONObject("userBaseInfo");
            int currentEnergy = userBaseInfo == null ? 0 : userBaseInfo.optInt("currentEnergy", 0);
            if (currentEnergy < 10) {
                Log.record("当前能量不足10g(" + currentEnergy + "g)，无法浇水");
                return;
            }

            // 获取服务端限制
            JSONObject miscJo = new JSONObject(AntForestRpcCall.queryMiscInfo("teamCanWaterCount", teamId));
            if (!MessageUtil.checkResultCode(TAG, miscJo)) {
                Log.record("queryMiscInfo 查询失败");
                return;
            }

            int serverRemaining = getTeamCanWaterCount(miscJo);
            Log.record("组队状态检查:目标剩余" + userRemainingQuota + "g|官方剩余" + serverRemaining + "g|背包能量" + currentEnergy + "g");

            if (serverRemaining < 10) {
                Log.record("官方限制今日无可浇水额度，跳过");
                return;
            }

            // 计算最终浇水量
            int finalWaterAmount = Math.min(userRemainingQuota, Math.min(serverRemaining, currentEnergy));
            if (finalWaterAmount < 10) {
                Log.record("计算后浇水量(" + finalWaterAmount + "g)低于最小限制10g，不执行");
                return;
            }

            // 执行浇水
            JSONObject waterJo = new JSONObject(AntForestRpcCall.teamWater(teamId, finalWaterAmount));
            if (MessageUtil.checkResultCode(TAG, waterJo)) {
                Log.forest("组队合种🚿[合种浇水]#" + finalWaterAmount + "g");
                Toast.show("组队合种🚿给合种浇水" + finalWaterAmount + "g");
                Status.forestHuntHelpToday("FLAG_TEAM_WATER_DAILY_COUNT", todayUsed + finalWaterAmount, UserIdMap.getCurrentUid());
                Log.record("组队合种今日浇水累计: " + (todayUsed + finalWaterAmount) + "g / " + userDailyTarget + "g");
            }
        } catch (Throwable t) {
            Log.err(TAG, "teamCooperateWater err:", t);
        } finally {
            // 任何分支（含异常和提前 return）都要切回个人模式，否则会一直停在组队模式影响其它功能
            if (switchedToTeam) {
                if (updateUserConfiginTeam(false)) {
                    Status.clearFlag(FLAG_TEAM_MODE_SWITCHED);
                    Log.record("已返回个人模式");
                } else {
                    Log.record("组队合种浇水后切回个人模式失败，下次运行会自动重试");
                }
            }
        }
    }

    /** 查询森林首页，失败返回 null */
    private static JSONObject queryTeamHomePage() {
        try {
            JSONObject homeJo = new JSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, homeJo)) {
                Log.record("queryHomePage 返回异常");
                return null;
            }
            return homeJo;
        } catch (Throwable t) {
            Log.err(TAG, "queryTeamHomePage err:", t);
        }
        return null;
    }

    /** 取组队合种的 teamId，缺失时返回空串 */
    private static String getTeamId(JSONObject homeJo) {
        JSONObject teamHomeResult = homeJo.optJSONObject("teamHomeResult");
        if (teamHomeResult == null) {
            return "";
        }
        JSONObject teamBaseInfo = teamHomeResult.optJSONObject("teamBaseInfo");
        return teamBaseInfo == null ? "" : teamBaseInfo.optString("teamId", "");
    }

    /** 取官方今日剩余可浇额度，缺失时返回 0 */
    private static int getTeamCanWaterCount(JSONObject miscJo) {
        JSONObject combineHandlerVOMap = miscJo.optJSONObject("combineHandlerVOMap");
        if (combineHandlerVOMap == null) {
            return 0;
        }
        JSONObject teamCanWaterCount = combineHandlerVOMap.optJSONObject("teamCanWaterCount");
        return teamCanWaterCount == null ? 0 : teamCanWaterCount.optInt("waterCount", 0);
    }

    /**
     * 组队合种浇水会临时把账号切到组队模式。若上次切回失败、或进程在切回前被杀，
     * 这里做一次校正，避免用户被永久留在组队模式（会影响个人主页 / 好友页的表现）。
     * <p>
     * 没开「组队合种浇水」也会执行：正是"开了功能 → 切换中途中断 → 用户随后把开关关掉"这种情况最需要校正。
     */
    private static void fixTeamModeIfNeeded() {
        if (!Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED)) {
            return;
        }
        if (updateUserConfiginTeam(false)) {
            Status.clearFlag(FLAG_TEAM_MODE_SWITCHED);
            Log.record("检测到上次组队合种浇水未切回，已恢复个人模式");
        } else {
            Log.record("上次组队合种浇水未切回个人模式，本次恢复失败，稍后重试");
        }
    }

    //needReturn:false切回个人模式，true切到组队模式
    private static boolean updateUserConfiginTeam(Boolean needReturn) {
        try {
            String updateStr = AntForestRpcCall.updateUserConfiginTeam(needReturn);
            JSONObject updateJo = new JSONObject(updateStr);
            if (!MessageUtil.checkResultCode(TAG, updateJo)) {
                Log.record("updateUserConfig 返回异常");
                return false;
            } else {
                Log.record("合种浇水切换成功：" + (needReturn ? "切到组队模式" : "切到个人模式"));
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "updateUserConfig err:", th);
        }
        return false;
    }

    /**
     * 组队状态：1=已在组队，0=确未组队，-1=无法判定（字段缺失/未知取值，如响应异常或新结构）。
     * 「无法判定」与「确未组队」必须区分：只有后者才允许触发模式切换等有副作用的动作。
     */
    private static int teamState(JSONObject homeObj) {
        if (homeObj == null || !homeObj.has("nextAction")) {
            return -1;
        }
        String nextAction = homeObj.optString("nextAction", "");
        if ("Cultivate".equals(nextAction)) {
            return 1;
        }
        if ("Team".equals(nextAction)) {
            return 0;
        }
        return -1;
    }

    private static boolean isTeam(JSONObject homeObj) {
        return teamState(homeObj) == 1;
    }

    private static void loveteam(int waterNum) {
        if (Status.hasFlagToday("Forest::loveteamWater")) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.loveteamHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject userInfo = jo.optJSONObject("userInfo");
            String teamId = userInfo == null ? "" : userInfo.optString("teamId", "");
            if (teamId.isEmpty()) {
                Log.record("真爱合种:未加入真爱合种或未取到队伍，跳过");
                return;
            }
            loveteamWater(teamId, getLoveteamName(jo, teamId), waterNum);
        } catch (Throwable th) {
            Log.err(TAG, "loveteam err:", th);
        }
    }

    /**
     * 真爱合种名称：loveHome 返回体里没有稳定已知的名称字段，这里按可能的 key 依次探测，
     * 都取不到时退回 teamId，保证日志始终有可读内容。
     */
    private static String getLoveteamName(JSONObject loveHome, String teamId) {
        JSONObject userInfo = loveHome.optJSONObject("userInfo");
        if (userInfo != null) {
            for (String key : new String[]{"teamName", "name", "treeName"}) {
                String name = userInfo.optString(key, "");
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        String name = loveHome.optString("teamName", "");
        return name.isEmpty() ? teamId : name;
    }

    private static void loveteamWater(String teamId, String teamName, int waterNum) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.loveteamWater(teamId, waterNum));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("真爱浇水🚿给[" + teamName + "]合种浇水" + waterNum + "g");
                Toast.show("真爱浇水🚿给[" + teamName + "]合种浇水" + waterNum + "g");
                Status.flagToday("Forest::loveteamWater");
            }
        } catch (Throwable th) {
            Log.err(TAG, "loveteamWater err:", th);
        }
    }

    private static boolean updateUserConfigEnergyPvp() {
        try {
            String Str = AntForestRpcCall.queryUserTag();
            JSONObject Jo = new JSONObject(Str);
            if (!MessageUtil.checkResultCode(TAG, Jo)) {
                Log.record("queryUserTag 查询1V1状态返回异常");
                return false;
            }
            if (Jo.has("tagMap")) {
                JSONObject tagMap = Jo.getJSONObject("tagMap");
                if (!tagMap.has("energyPvp")) {
                    Log.record("查询1V1无状态返回[无法参加]若出现标识立马为大人开启");
                    return false;
                }
                String energyPvp = tagMap.getString("energyPvp");
                if (energyPvp.equals("Y")) {
                    return true;
                }
            }
            //自动开启挑战
            String updateStr = AntForestRpcCall.updateUserConfigEnergyPvp(true);
            JSONObject updateJo = new JSONObject(updateStr);
            if (!MessageUtil.checkResultCode(TAG, updateJo)) {
                Log.record("updateUserConfigEnergyPvp 返回异常");
            } else {
                Log.forest("比赛情况🆚1V1能量挑战成功开启");
                return true;
            }
            return false;
        } catch (Throwable th) {
            Log.err(TAG, "updateUserConfigEnergyPvp err:", th);
        }
        return false;
    }

    private static void queryPvpHomeInfo() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryPvpHomeInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (jo.has("currentEnergyPvpBattleRecord")) {
                JSONObject currentEnergyPvpBattleRecord = jo.getJSONObject("currentEnergyPvpBattleRecord");
                //获取1v1比赛情况
                String attackerDisplayName = currentEnergyPvpBattleRecord.optString("attackerDisplayName");
                int attackerEnergy = currentEnergyPvpBattleRecord.optInt("attackerEnergy");
                int attackerWinCount = currentEnergyPvpBattleRecord.optInt("attackerWinCount");
                String battleType = currentEnergyPvpBattleRecord.optString("battleType");
                String defenderDisplayName = currentEnergyPvpBattleRecord.optString("defenderDisplayName");
                int defenderEnergy = currentEnergyPvpBattleRecord.optInt("defenderEnergy");
                int defenderWinCount = currentEnergyPvpBattleRecord.optInt("defenderWinCount");
                String battleStatus = currentEnergyPvpBattleRecord.optString("battleStatus");
                Log.record("比赛情况🆚" + battleType + "赛" + battleStatus + "[" + attackerDisplayName + "]" + attackerWinCount + "胜(" + attackerEnergy + "g)VS[" + defenderDisplayName + "]" + attackerWinCount + "胜(" + defenderEnergy + "g)");
                //领取奖励
                if (currentEnergyPvpBattleRecord.optBoolean("hasReward")) {
                    jo = new JSONObject(AntForestRpcCall.receivePvpRewards());
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    if (jo.has("receivedRewards")) {
                        JSONArray receivedRewards = jo.getJSONArray("receivedRewards");
                        for (int i = 0; i < receivedRewards.length(); i++) {
                            JSONObject reward = receivedRewards.getJSONObject(i);
                            String rewardName = reward.getString("rewardName");
                            String rewardType = reward.getString("rewardType");
                            if ("energy".equals(rewardType)) {
                                int energy = reward.getInt("energy");
                                Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                                Statistics.addData(Statistics.DataType.COLLECTED, energy);
                            } else {
                                Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                            }
                        }
                    }

                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryPvpHomeInfo err:", th);
        }

    }

    private static void receivePvpRewards() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.checkRewardqueryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (jo.has("combineHandlerVOMap")) {
                JSONObject combineHandlerVOMap = jo.getJSONObject("combineHandlerVOMap");
                if (combineHandlerVOMap.has("energyPvpInfo")) {
                    JSONObject energyPvpInfo = combineHandlerVOMap.getJSONObject("energyPvpInfo");
                    //领取奖励
                    if (energyPvpInfo.optBoolean("hasReward")) {
                        jo = new JSONObject(AntForestRpcCall.receivePvpRewards());
                        if (!MessageUtil.checkResultCode(TAG, jo)) {
                            return;
                        }
                        if (jo.has("receivedRewards")) {
                            JSONArray receivedRewards = jo.getJSONArray("receivedRewards");
                            for (int i = 0; i < receivedRewards.length(); i++) {
                                JSONObject reward = receivedRewards.getJSONObject(i);
                                String rewardName = reward.getString("rewardName");
                                String rewardType = reward.getString("rewardType");
                                if ("energy".equals(rewardType)) {
                                    int energy = reward.getInt("energy");
                                    Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, energy);
                                } else {
                                    Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "receivePvpRewards err:", th);
        }
    }


    private void dress() {
        String dressDetail = dressDetailList.getValue();
        if (dressDetail.isEmpty()) {
            setDressDetail(getDressDetail().toString());
        } else {
            checkDressDetail(dressDetail);
        }
    }

    private JSONObject getDressDetail() {
        JSONObject dressDetail = new JSONObject();
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryHomePage());
            JSONArray ja = jo.getJSONObject("indexDressVO").getJSONArray("dressDetailList");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                String position = jo.getString("position");
                String batchType = jo.getString("batchType");
                dressDetail.put(position, batchType);
            }
        } catch (Throwable th) {
            Log.err(TAG, "getDressDetail err:", th);
        }
        return dressDetail;
    }

    private void setDressDetail(String dressDetail) {
        dressDetailList.setValue(dressDetail);
        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
            Log.forest("装扮保护🔐皮肤保存,芝麻粒将为你持续保护!");
        }
    }

    private void removeDressDetail(String position) {
        JSONObject jo = getDressDetail();
        jo.remove(position);
        setDressDetail(jo.toString());
    }

    private void checkDressDetail(String dressDetail) {
        String[] positions = {"tree__main", "bg__sky_0", "bg__sky_cloud", "bg__ground_a", "bg__ground_b", "bg__ground_c"};
        try {
            boolean isDressExchanged = false;
            JSONObject jo = new JSONObject(dressDetail);
            for (String position : positions) {
                String batchType = "";
                if (jo.has(position)) {
                    batchType = jo.getString(position);
                }
                if (queryUserDressForBackpack(dressMap.get(position), batchType)) {
                    isDressExchanged = true;
                }
            }
            if (isDressExchanged) {
                Log.forest("装扮保护🔐皮肤修改,芝麻粒已为你自动恢复!");
            }
        } catch (Throwable th) {
            Log.err(TAG, "checkDressDetail err:", th);
        }
    }

    private Boolean queryUserDressForBackpack(String positionType, String batchType) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.listUserDressForBackpack(positionType));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray userHoldDressVOList = jo.getJSONArray("userHoldDressVOList");
            boolean isTakeOff = false;
            for (int i = 0; i < userHoldDressVOList.length(); i++) {
                jo = userHoldDressVOList.getJSONObject(i);
                if (jo.optInt("remainNum", 1) == 0) {
                    if (batchType.equals(jo.getString("batchType"))) {
                        return false;
                    }
                    String position = jo.getJSONArray("posList").getString(0);
                    isTakeOff = takeOffDress(jo.getString("dressType"), position);
                } else if (batchType.equals(jo.getString("batchType"))) {
                    return wearDress(jo.getString("dressType"));
                }
            }

            if (!batchType.isEmpty()) {
                removeDressDetail(dressMap.get(positionType));
                Log.forest("装扮保护🔐皮肤过期,芝麻粒已为你恢复默认!");
            }
            return isTakeOff;
        } catch (Throwable th) {
            Log.err(TAG, "queryUserDressForBackpack err:", th);
        }
        return false;
    }

    private Boolean wearDress(String dressType) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.wearDress(dressType));
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "wearDress err:", th);
        }
        return false;
    }

    private Boolean takeOffDress(String dressType, String position) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.takeOffDress(dressType, position));
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "takeOffDress err:", th);
        }
        return false;
    }

    /**
     * The enum Collect status.
     */
    public enum CollectStatus {
        /**
         * Available collect status.
         */
        AVAILABLE,
        /**
         * Waiting collect status.
         */
        WAITING,
        /**
         * Insufficient collect status.
         */
        INSUFFICIENT,
        /**
         * Robbed collect status.
         */
        ROBBED
    }

    /**
     * The type Bubble timer task.
     */
    private class BubbleTimerTask extends ChildModelTask {

        /**
         * The User id.
         */
        private final String userId;

        /**
         * The Bubble id.
         */
        private final long bubbleId;

        /**
         * The ProduceTime.
         */
        private final long produceTime;
        private final String userName;

        /**
         * Instantiates a new Bubble timer task.
         */
        BubbleTimerTask(String ui, long bi, long pt, String un) {
            super(AntForestV2.getBubbleTimerTid(ui, bi), pt - advanceTimeInt);
            userId = ui;
            bubbleId = bi;
            produceTime = pt;
            userName = un;
        }

        @Override
        public Runnable setRunnable() {
            return () -> {
                //String userName = UserIdMap.getMaskName(userId);
                int averageInteger = offsetTimeMath.getAverageInteger();
                long readyTime = produceTime - advanceTimeInt + averageInteger - delayTimeMath.getAverageInteger() - System.currentTimeMillis() + 70;
                if (readyTime > 0) {
                    try {
                        Thread.sleep(readyTime);
                    } catch (InterruptedException e) {
                        Log.i("终止[" + userName + "]蹲点收取任务, 任务ID[" + getId() + "]");
                        return;
                    }
                }
                Log.record("执行蹲点收取[" + userName + "]" + "时差[" + averageInteger + "]ms" + "提前[" + advanceTimeInt + "]ms");
                collectEnergy(new CollectEnergyEntity(userId, null, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), userName);
            };
        }
    }

    @Override
    public CollectResult collectUserEnergyForWaiting(List<EnergyWaitingTask> tasks) {
        int collectedTotal = 0;
        boolean allSuccess = true;
        String lastError = null;
        for (EnergyWaitingTask task : tasks) {
            int before = totalCollected;
            try {
                collectEnergy(new CollectEnergyEntity(task.getUserId(), null, AntForestRpcCall.getCollectEnergyRpcEntity(null, task.getUserId(), task.getBubbleId())), true, task.getUserName());
            } catch (Throwable t) {
                Log.printStackTrace(t);
            }
            int gained = totalCollected - before;
            if (gained > 0) {
                collectedTotal += gained;
            } else {
                allSuccess = false;
                lastError = "收取失败";
            }
        }
        return allSuccess ? CollectResult.success(collectedTotal) : CollectResult.failure(lastError);
    }

    @Override
    public void addToTotalCollected(int energyCount) {
    }

    @Override
    public long getWaitingCollectDelay() {
        return 0L;
    }

    @Override
    public void onPreRevalidateProp(String userId, String fromTag) {
    }

    @Override
    public void onSmartPropDecision(Collection<EnergyWaitingTask> allWaitingTasks) {
    }

    public static String getBubbleTimerTid(String ui, long bi) {
        return "BT|" + ui + "|" + bi;
    }

    public enum ItemStatus {
        NO_ENOUGH_POINT, NO_ENOUGH_STOCK, REACH_LIMIT, SECKILL_NOT_BEGIN, SECKILL_HAS_END, HAS_NEVER_EXPIRE_DRESS;

        public static final String[] nickNames = {"活力值不足", "库存量不足", "兑换达上限", "秒杀未开始", "秒杀已结束", "不限时皮肤"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }

    public enum PropGroup {
        shield, boost, doubleClick, energyRain, vitalitySignDouble, stealthCard, robExpandCard;

        public static final String[] nickNames = {"能量保护罩", "时光加速器", "能量双击卡", "能量雨卡", "活力翻倍卡", "隐身卡", "能量翻倍卡"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }

    public interface WaterFriendType {

        int WATER_00 = 0;
        int WATER_10 = 1;
        int WATER_18 = 2;
        int WATER_33 = 3;
        int WATER_66 = 4;

        String[] nickNames = {"不浇水", "浇水10克", "浇水18克", "浇水33克", "浇水66克"};
        int[] waterEnergy = {0, 10, 18, 33, 66};
    }

    public interface HelpFriendCollectType {

        int NONE = 0;
        int HELP = 1;
        int NOT_HELP = 2;

        String[] nickNames = {"不复活能量", "复活已选好友", "复活未选好友"};
    }

    public interface ConsumeAnimalPropType {

        int NONE = 0;
        int SEQUENCE = 1;
        int QUANTITY = 2;

        String[] nickNames = {"不派遣动物", "按默认顺序派遣", "按最多能量派遣"};
    }

    public interface UsePropType {

        int CLOSE = 0;
        int ALL = 1;
        int ONLY_LIMIT_TIME = 2;

        String[] nickNames = {"关闭", "所有道具", "限时道具"};
    }

    public interface CollectSelfType {
        int ALL = 0;
        int OVER_THRESHOLD = 1;
        int BELOW_THRESHOLD = 2;

        String[] nickNames = {"所有", "大于阈值", "小于阈值"};
    }

    public interface whackModeNames {
        int CLOSE = 0;
        int WHACK_MODE_COMPATIBLE = 1;
        int WHACK_MODE_AGGRESSIVE = 2;
        String[] nickNames = {"关闭", "兼容模式", "激进模式"};
    }

    }
