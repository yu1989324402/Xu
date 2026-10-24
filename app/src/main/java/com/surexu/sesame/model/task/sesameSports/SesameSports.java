package com.surexu.sesame.model.task.sesameSports;

import android.content.Context;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.modelFieldExt.IntegerModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.hook.ApplicationHook;
import com.surexu.sesame.hook.MtopSender;
import com.surexu.sesame.hook.SesameDogAuth;
import com.surexu.sesame.model.base.TaskCommon;
import com.surexu.sesame.util.FileUtil;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.StringUtil;
import com.surexu.sesame.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 体育游乐记(alisports「dog」活动)自动任务。
 *
 * <p>与上一轮芝麻粒游乐记(芝麻狗)的关键差异:
 * <ol>
 *   <li>接口前缀从 {@code mtop.alisports.play.sesamedog.*} 换成 {@code mtop.alisports.play.dog.common.*};</li>
 *   <li>前进消耗的是**体力**(抓包: singleConsumePoints=2000, 每次前进 forwardPoints=2000),
 *       体力由活动任务中心的任务发放(benefitHSFAddress=1.0.0-DOG_POINT);</li>
 *   <li>因此本任务按用户要求先「领体力」再跑: 跑前调 {@code dog.common.refreshPoints} 做一次
 *       刷新/结算(抓包里活动页点体力条就是这个接口), 再只读盘点任务中心的任务与可领体力;</li>
 *   <li>新增前置判断 {@code dog.common.obtainPlayState}, 只有 PLAYING 才前进。</li>
 * </ol>
 *
 * <p>线程约定: 由 ModelTask 的任务线程调用, 内部全是阻塞式 MTOP 调用。
 */
public class SesameSports extends ModelTask {

    private static final String TAG = SesameSports.class.getSimpleName();

    private static final String FLAG_DEBUG_FILE = "sesame_sports_debug.log";
    /** 单轮前进次数的默认保护上限(每 1.2~2.6s 一次请求, 200 次约 6~8 分钟)。 */
    private static final int DEFAULT_FORWARD_LIMIT = 200;
    /** 保护上限的硬顶, 避免误填导致长时间连续请求 */
    private static final int HARD_FORWARD_LIMIT = 2000;
    private static final int MAX_RECORDS_PER_ROUND = 20;
    private static final int MAX_DEBUG_CHARS = 20000;
    /** 每前进 N 次拉一次 index, 校准体力余额并判断该城市是否走完(省请求) */
    private static final int CALIBRATE_EVERY = 5;
    private static final long SWITCH_INTERVAL_MS = 800L;
    /** 前进之间的随机间隔, 放慢避免风控 */
    private static final long FORWARD_INTERVAL_MIN_MS = 1200L;
    private static final long FORWARD_INTERVAL_MAX_MS = 2600L;

    /** 跑前先刷新/领取体力(先领体力再跑) */
    private BooleanModelField autoSupplyPoints;
    /** 自动领取任务中心里已完成但未领取的任务奖励(体力/卡币, 接口抓包已确认) */
    private BooleanModelField autoClaimTaskReward;
    /** 体力还有时自动切换到其他城市记录继续跑 */
    private BooleanModelField autoSwitchRecord;
    /** 单轮前进次数上限保护 */
    private IntegerModelField maxForwardPerRun;

    private StringBuilder debugDump;
    private int debugWarnCount;

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "体育游乐记";
    }

    public SesameSports() {
        autoSupplyPoints = new BooleanModelField("autoSupplyPoints", "跑前先领体力(刷新结算)", true);
        autoClaimTaskReward = new BooleanModelField("autoClaimTaskReward",
                "自动领取任务奖励(体力/卡币)", true);
        autoSwitchRecord = new BooleanModelField("autoSwitchRecord", "体力富余时自动换城市继续跑", true);
        maxForwardPerRun = new IntegerModelField("maxForwardPerRun", "单轮前进次数上限(保护)",
                DEFAULT_FORWARD_LIMIT, 1, HARD_FORWARD_LIMIT);
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(autoSupplyPoints);
        modelFields.addField(autoClaimTaskReward);
        modelFields.addField(autoSwitchRecord);
        modelFields.addField(maxForwardPerRun);
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️体育游乐记:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            // 任务对象跨轮复用, 排障缓冲区必须每轮重置, 否则第二轮起会被 MAX_DEBUG_CHARS 掐掉
            debugDump = null;
            debugWarnCount = 0;
            Log.record("体育游乐记: 开始 | " + MtopSender.stateText() + " " + SesameDogAuth.stateText());
            debug("base", MtopSender.stateText() + " " + SesameDogAuth.stateText());

            // 0) 路由探测: 活动改版/下线就别跑了
            MtopSender.Result pageroute = SesameSportsRpcCall.pageroute();
            debug("pageroute", describeRaw(pageroute));
            if (pageroute == null) {
                Log.record("体育游乐记⏸️ 活动探测失败: 无可用的授权 token");
                flushDebug(0, 0, -1);
                return;
            }
            if (!pageroute.ok) {
                warn("pageroute", pageroute);
                Log.record("体育游乐记⏸️ 活动探测失败: " + pageroute.brief() + " | " + pageroute.note);
                flushDebug(0, 0, -1);
                return;
            }
            JSONObject routeBody = pageroute.body();
            String route = routeBody == null ? "" : routeBody.optString("routeRedirectType");
            if (!StringUtil.isEmpty(route) && !"DOG_HOME".equals(route)) {
                Log.record("体育游乐记⏸️ 活动路由=" + route + "(非 DOG_HOME), 本次不执行");
                flushDebug(0, 0, -1);
                return;
            }

            // 1) 活动状态: 体育游乐记特有前置, 非 PLAYING 不前进
            MtopSender.Result stateResult = SesameSportsRpcCall.playState();
            debug("playState", describeRaw(stateResult));
            String playState = playStateOf(stateResult);
            if (StringUtil.isEmpty(playState)) {
                warn("playState", stateResult);
                Log.record("体育游乐记⏸️ 活动状态读取失败: "
                        + (stateResult == null ? "无响应" : stateResult.brief() + " | " + stateResult.note));
                flushDebug(0, 0, -1);
                return;
            }
            if (!"PLAYING".equals(playState)) {
                Log.record("体育游乐记⏸️ 活动状态=" + playState + "(非 PLAYING), 本次不前进");
                flushDebug(0, 0, -1);
                return;
            }

            // 2) 首页: 当前城市/记录/节点状态/体力
            MtopSender.Result index = SesameSportsRpcCall.index();
            debug("index", describeRaw(index));
            if (index == null) {
                Log.record("体育游乐记⏸️ 未取到 alisports 授权 token, 本轮无法前进");
                flushDebug(0, 0, -1);
                return;
            }
            if (!index.ok) {
                warn("index", index);
                Log.record("体育游乐记⏸️ 首页返回失败: " + index.brief() + " | " + index.note);
                flushDebug(0, 0, -1);
                return;
            }
            JSONObject indexBody = index.body();
            JSONObject playing = indexBody == null ? null : indexBody.optJSONObject("playingIndexResult");
            String nodeState = indexBody == null ? "" : indexBody.optString("nodeState");
            String playingId = playing == null ? "" : playing.optString("playingId");
            String cityName = "";
            JSONObject city = playing == null ? null : playing.optJSONObject("playingCity");
            if (city != null) {
                cityName = city.optString("cityName");
            }
            double currentProgress = playing == null ? -1 : playing.optDouble("progress", -1);
            int points = pointsOf(indexBody);
            int unit = singleConsumeOf(indexBody);
            int pointsBefore = points;
            Log.record("体育游乐记: 当前城市=" + (StringUtil.isEmpty(cityName) ? "?" : cityName)
                    + " 记录=" + playingId + " 状态=" + nodeState
                    + " 体力=" + (points < 0 ? "?" : String.valueOf(points))
                    + " 单次消耗=" + unit
                    + (currentProgress < 0 ? "" : " 城市进度=" + percent(currentProgress)));
            if (!"PLAYING".equals(nodeState)) {
                Log.record("体育游乐记⏸️ 当前节点状态=" + nodeState + ", 本次不前进");
                flushDebug(0, 0, points);
                return;
            }

            // 3) 先领体力: 刷新结算 + 任务中心盘点(可选领取)
            if (autoSupplyPoints.getValue()) {
                int supplied = supplyPoints(points, unit);
                if (supplied >= 0) {
                    points = supplied;
                }
            }
            if (points >= 0 && points < unit) {
                Log.record("体育游乐记⏸️ 体力不足(" + points + " < " + unit + "), 请先在活动页完成任务并在"
                        + "任务中心领取体力, 领取后重跑本任务");
                flushDebug(0, 0, points);
                return;
            }

            // 4) 参与记录: 一个记录=一个城市
            List<Record> records = listRecords(playingId, cityName);
            if (records.isEmpty()) {
                Log.record("体育游乐记⏸️ 没有可用的游玩记录");
                flushDebug(0, 0, points);
                return;
            }
            logRecords(records);

            int ceiling = maxForwardPerRun.getValue();
            int currentIndex = indexOf(records, playingId);
            List<Record> order;
            if (autoSwitchRecord.getValue()) {
                order = rotateFrom(records, currentIndex);
            } else {
                order = new ArrayList<>();
                if (currentIndex >= 0) {
                    order.add(records.get(currentIndex));
                } else {
                    order.add(records.get(0));
                }
                Log.record("体育游乐记: 自动换城市已关闭, 只跑当前城市记录");
            }

            String currentId = playingId;
            int totalForward = 0;
            int switched = 0;
            int consecutiveFail = 0;
            for (Record rec : order) {
                if (totalForward >= ceiling) {
                    Log.record("体育游乐记: 已达单轮前进保护上限(" + ceiling + " 次), 本轮结束");
                    break;
                }
                if (rec.finished) {
                    Log.record("体育游乐记: 跳过 " + rec.label() + "(该城市已走完)");
                    continue;
                }
                if (!rec.playingId.equals(currentId)) {
                    MtopSender.Result change = SesameSportsRpcCall.changeRecord(rec.playingId);
                    debug("change:" + rec.playingId, describeRaw(change));
                    if (change == null || !change.ok) {
                        warn("change", change);
                        Log.record("体育游乐记⚠️ 切换到 " + rec.label() + " 失败: "
                                + (change == null ? "无响应" : change.brief()));
                        continue;
                    }
                    currentId = rec.playingId;
                    switched++;
                    Log.record("体育游乐记: 已切换城市 → " + rec.label());
                    TimeUtil.sleep(SWITCH_INTERVAL_MS);
                }
                DrainResult dr = drain(rec, ceiling - totalForward, unit, points);
                points = dr.balance;
                totalForward += dr.done;
                if (dr.stopByPoints) {
                    Log.record("体育游乐记: 体力耗尽, 本轮结束");
                    break;
                }
                if (dr.hardFail) {
                    if (++consecutiveFail >= 2) {
                        Log.record("体育游乐记⏸️ 接口连续失败, 本轮提前结束");
                        break;
                    }
                } else {
                    consecutiveFail = 0;
                }
            }

            // 5) 跑完切回用户原来的当前城市, 免得活动页停在最后一个被刷的城市
            if (autoSwitchRecord.getValue() && !StringUtil.isEmpty(playingId) && !playingId.equals(currentId)) {
                MtopSender.Result back = SesameSportsRpcCall.changeRecord(playingId);
                debug("restore:" + playingId, describeRaw(back));
                if (back != null && back.ok) {
                    Log.record("体育游乐记: 已切回原当前城市记录 " + playingId);
                }
            }

            // 6) 汇总
            if (totalForward == 0 && switched == 0) {
                Log.record("体育游乐记: 没有可前进的城市记录(体力不足或城市均已走完)");
            } else {
                Log.record("体育游乐记: 本轮完成 | 切换城市 " + switched + " 个 | 前进 " + totalForward
                        + " 次 | 体力 " + (pointsBefore < 0 ? "?" : String.valueOf(pointsBefore))
                        + " → " + (points < 0 ? "?" : String.valueOf(points)));
                Log.record("体育游乐记: 记录情况 " + remainText(records));
            }
            flushDebug(totalForward, switched, points);
        } catch (Throwable t) {
            Log.err(TAG, "run err:", t);
            debug("exception", String.valueOf(t));
            flushDebug(-1, -1, -1);
        }
    }

    // ═══════════════════════════ 领体力 ═══════════════════════════

    /**
     * 跑前补给: ①调 refreshPoints 刷新/结算体力; ②只读盘点任务中心, 有已完成未领取的任务时
     * 视开关尝试领取, 领完再结算一次。返回结算后的体力(取不到返回 -1)。
     */
    private int supplyPoints(int points, int unit) {
        Log.record("体育游乐记: 跑前补给体力(刷新结算) | 当前体力=" + (points < 0 ? "?" : String.valueOf(points)));
        int result = -1;
        MtopSender.Result r = SesameSportsRpcCall.refreshPoints();
        debug("refreshPoints", describeRaw(r));
        if (r == null) {
            Log.record("体育游乐记⚠️ 刷新体力无响应(授权 token 不可用)");
        } else if (!r.ok) {
            warn("refreshPoints", r);
            Log.record("体育游乐记⚠️ 刷新体力失败: " + r.brief() + " | " + r.note);
        } else {
            JSONObject body = r.body();
            int p = pointsOf(body);
            int u = body == null ? unit : toInt(body.optString("singleConsumePoints"), unit);
            if (p >= 0) {
                result = p;
            }
            Log.record("体育游乐记: 刷新结算后 体力=" + (p < 0 ? "?" : String.valueOf(p))
                    + " 单次消耗=" + u
                    + (points >= 0 && p >= 0 ? "(本次结算 " + (p - points) + ")" : ""));
        }
        int afterTask = supplyByTaskCenter();
        if (afterTask >= 0) {
            result = afterTask;
        }
        return result;
    }

    /** 任务中心盘点(只读) + 可选领取; 返回领取后再结算的体力, 取不到 -1。 */
    private int supplyByTaskCenter() {
        MtopSender.Result r = SesameSportsRpcCall.taskModuleInfo();
        debug("taskModuleInfo", describeRaw(r));
        if (r == null || !r.ok) {
            warn("taskModuleInfo", r);
            Log.record("体育游乐记: 任务中心读取失败("
                    + (r == null ? "无响应" : r.brief() + " | " + r.note) + "), 跳过任务体力盘点");
            return -1;
        }
        List<SesameSportsRpcCall.TaskItem> tasks = SesameSportsRpcCall.parseTasks(r);
        if (tasks.isEmpty()) {
            Log.record("体育游乐记: 任务中心暂无任务");
            return -1;
        }
        int pending = 0;
        int pendingPoints = 0;
        int claimed = 0;
        int todo = 0;
        int todoPoints = 0;
        List<SesameSportsRpcCall.TaskItem> claimable = new ArrayList<>();
        for (SesameSportsRpcCall.TaskItem task : tasks) {
            if (task.claimed()) {
                claimed++;
            } else if (task.done()) {
                pending++;
                pendingPoints += task.reward;
                claimable.add(task);
            } else {
                todo++;
                todoPoints += task.reward;
            }
        }
        Log.record("体育游乐记: 任务中心 " + tasks.size() + " 个任务(今日已完成 "
                + SesameSportsRpcCall.finishedToday(r) + " 个) | 已完成待领取 " + pending + " 个(约"
                + pendingPoints + "体力) | 已领取 " + claimed + " 个 | 未完成 " + todo + " 个(约"
                + todoPoints + "体力)");
        for (SesameSportsRpcCall.TaskItem task : tasks) {
            Log.debug("[" + TAG + "] task " + task);
        }
        if (claimable.isEmpty()) {
            return -1;
        }
        if (!autoClaimTaskReward.getValue()) {
            Log.record("体育游乐记: 有 " + pending + " 个任务已完成但未领取(约" + pendingPoints
                    + "体力), 请在活动页/任务中心点「领取」, 领取后重跑本任务即可用掉这些体力");
            return -1;
        }
        int gotPoints = 0;
        int gotKabi = 0;
        int failed = 0;
        for (SesameSportsRpcCall.TaskItem task : claimable) {
            MtopSender.Result claim = SesameSportsRpcCall.claimTaskReward(task.taskId);
            debug("claim:" + task.taskId, describeRaw(claim));
            if (claim != null && claim.ok) {
                int p = SesameSportsRpcCall.claimedPoints(claim);
                int k = SesameSportsRpcCall.claimedKabi(claim);
                if (p > 0) {
                    gotPoints += p;
                }
                if (k > 0) {
                    gotKabi += k;
                }
                Log.record("体育游乐记: 已领取任务奖励 " + (p >= 0 ? p + "体力" : "?")
                        + (k > 0 ? " + " + k + "卡币" : "") + "(" + task.title + ", 任务卡面额 "
                        + task.reward + "体力; 连续任务按档发放)");
                TimeUtil.sleep(1500L);
            } else if (SesameSportsRpcCall.isAlreadyClaimed(claim)) {
                Log.record("体育游乐记: 任务已领取过, 跳过(" + task.title + ")");
            } else {
                failed++;
                warn("claim", claim);
                Log.record("体育游乐记⚠️ 领取任务奖励失败(" + task.title + "): "
                        + (claim == null ? "无响应" : claim.brief() + " | " + claim.note));
            }
        }
        if (claimable.size() > 1) {
            Log.record("体育游乐记: 本轮领奖合计 " + gotPoints + "体力"
                    + (gotKabi > 0 ? " + " + gotKabi + "卡币" : "")
                    + (failed > 0 ? ", 失败 " + failed + " 个(详见模块日志)" : ""));
        }
        MtopSender.Result again = SesameSportsRpcCall.refreshPoints();
        debug("refreshPoints2", describeRaw(again));
        if (again != null && again.ok) {
            int p = pointsOf(again.body());
            Log.record("体育游乐记: 领取后再结算 体力=" + (p < 0 ? "?" : String.valueOf(p)));
            return p;
        }
        return -1;
    }

    // ═══════════════════════════ 前进 ═══════════════════════════

    /** 把一条城市记录跑到体力不足 / 城市走完 / 打满安全上限。 */
    private DrainResult drain(Record rec, int budget, int unit, int startBalance) {
        DrainResult out = new DrainResult();
        int balance = startBalance;
        for (int i = 0; i < budget; i++) {
            if (balance >= 0 && balance < unit) {
                out.stopByPoints = true;
                Log.record("体育游乐记: 体力不足(" + balance + " < " + unit + "), 停止前进");
                break;
            }
            MtopSender.Result r = SesameSportsRpcCall.forward(rec.playingId);
            if (r == null) {
                out.hardFail = true;
                Log.record("体育游乐记⚠️ " + rec.label() + " 前进失败: 无响应(授权 token 不可用)");
                break;
            }
            if (r.ok) {
                out.done++;
                rec.drained++;
                JSONObject body = r.body();
                JSONObject info = body == null ? null : body.optJSONObject("userPointsInfo");
                if (info != null) {
                    // 抓包实测: forward 返回的 points 是「本次消耗前」的余额, 所以要再扣一次 unit
                    int before = toInt(info.optString("points"), -1);
                    if (before >= 0) {
                        balance = before - unit;
                    } else if (balance >= 0) {
                        balance -= unit;
                    }
                    String forwardPoints = info.optString("forwardPoints");
                    if (!StringUtil.isEmpty(forwardPoints)) {
                        out.lastState = "站点进度+" + forwardPoints;
                    }
                } else if (balance >= 0) {
                    balance -= unit;
                }
                debug("forward:" + rec.playingId, describeRaw(r));
                if (out.done % CALIBRATE_EVERY == 0) {
                    // 周期性校准: 体力余额以服务端为准, 顺带看该城市是否走完
                    MtopSender.Result cal = SesameSportsRpcCall.index();
                    debug("calibrate", describeRaw(cal));
                    if (cal != null && cal.ok) {
                        int p = pointsOf(cal.body());
                        if (p >= 0) {
                            balance = p;
                            out.calibrated = true;
                        }
                        JSONObject calPlaying = cal.body() == null ? null
                                : cal.body().optJSONObject("playingIndexResult");
                        double progress = calPlaying == null ? -1 : calPlaying.optDouble("progress", -1);
                        String site = calPlaying == null ? "" : calPlaying.optString("currentSiteName");
                        out.lastState = "体力=" + (p < 0 ? "?" : String.valueOf(p))
                                + (StringUtil.isEmpty(site) ? "" : " 站点=" + site)
                                + (progress < 0 ? "" : " 进度=" + percent(progress));
                        if (progress >= 1.0d) {
                            rec.finished = true;
                            out.recordFinished = true;
                            Log.record("体育游乐记: " + rec.label() + " 已走完全程");
                            break;
                        }
                    }
                }
            } else if (r.isDailyLimit()) {
                out.reachedLimit = true;
                rec.finished = true;
                Log.record("体育游乐记: " + rec.label() + " 已达服务端上限(本次前进 " + out.done + " 次)");
                debug("forward:limit:" + rec.playingId, describeRaw(r));
                break;
            } else if (r.isPointsNotEnough()) {
                out.stopByPoints = true;
                Log.record("体育游乐记: " + rec.label() + " 前进被拒(体力不足): " + r.brief());
                debug("forward:points:" + rec.playingId, describeRaw(r));
                break;
            } else {
                out.hardFail = true;
                warn("forward", r);
                Log.record("体育游乐记⚠️ " + rec.label() + " 前进失败: " + r.brief() + " | " + r.note);
                debug("forward:fail:" + rec.playingId, describeRaw(r));
                break;
            }
            if (i + 1 < budget) {
                TimeUtil.sleep(randomDelay());
            }
        }
        out.balance = balance;
        if (out.done > 0) {
            Log.record("体育游乐记: " + rec.label() + " 前进 " + out.done + " 次(消耗体力 " + out.done * unit
                    + ", 剩余体力约 " + (balance < 0 ? "?" : String.valueOf(balance)) + ")"
                    + (StringUtil.isEmpty(out.lastState) ? "" : " | " + out.lastState));
        }
        if (!out.reachedLimit && !out.stopByPoints && !out.hardFail && !out.recordFinished
                && out.done >= budget && budget > 0) {
            Log.record("体育游乐记⚠️ " + rec.label() + " 已达单轮保护上限(" + budget
                    + " 次)但未报上限; 需要继续请调大「单轮前进次数上限」");
        }
        return out;
    }

    // ═══════════════════════════ 记录 ═══════════════════════════

    private List<Record> listRecords(String fallbackId, String fallbackCity) {
        List<Record> list = new ArrayList<>();
        MtopSender.Result r = SesameSportsRpcCall.listRecords();
        debug("listRecords", describeRaw(r));
        if (r == null || !r.ok) {
            warn("listRecords", r);
            Log.record("体育游乐记⚠️ 城市记录拉取失败: "
                    + (r == null ? "无响应" : r.brief() + " | " + r.note));
        } else {
            JSONObject body = r.body();
            JSONArray array = body == null ? null : body.optJSONArray("result");
            if (array != null) {
                for (int i = 0; i < array.length() && list.size() < MAX_RECORDS_PER_ROUND; i++) {
                    JSONObject o = array.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    String id = o.optString("playingId");
                    if (StringUtil.isEmpty(id)) {
                        continue;
                    }
                    String siteName = o.optString("currentSiteName");
                    if (StringUtil.isEmpty(siteName)) {
                        siteName = lastSiteName(o.optJSONArray("siteConfig"));
                    }
                    Record rec = new Record(id, o.optString("cityName"), siteName,
                            o.optDouble("progress", -1), toLong(o.optString("usedPoints"), -1),
                            toLong(o.optString("unlockPoints"), -1), o.optBoolean("selectFlag", false));
                    list.add(rec);
                }
            }
        }
        if (list.isEmpty() && !StringUtil.isEmpty(fallbackId)) {
            // 列表拿不到时用首页的当前城市兜底, 至少还能跑当前记录
            list.add(new Record(fallbackId, fallbackCity, "", -1, -1, -1, true));
        }
        return list;
    }

    private void logRecords(List<Record> records) {
        Log.record("体育游乐记: 城市记录 " + records.size() + " 条 | " + remainText(records));
        for (Record rec : records) {
            Log.debug("[" + TAG + "] record " + rec.playingId + " " + rec.cityName + "/" + rec.siteName
                    + " progress=" + rec.progress + " used=" + rec.usedPoints + " unlock=" + rec.unlockPoints
                    + " current=" + rec.selectFlag + " finished=" + rec.finished);
        }
    }

    private static String remainText(List<Record> records) {
        StringBuilder sb = new StringBuilder();
        for (Record rec : records) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(rec.label());
            if (rec.finished) {
                sb.append("(已走完)");
            } else if (rec.progress >= 0) {
                sb.append("(").append(percent(rec.progress)).append(")");
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static String lastSiteName(JSONArray siteConfig) {
        if (siteConfig == null) {
            return "";
        }
        for (int i = siteConfig.length() - 1; i >= 0; i--) {
            JSONObject site = siteConfig.optJSONObject(i);
            if (site == null) {
                continue;
            }
            if (site.optBoolean("reachEndpoint", false)) {
                continue;
            }
            String name = site.optString("siteName");
            if (!StringUtil.isEmpty(name)) {
                return name;
            }
        }
        JSONObject first = siteConfig.optJSONObject(0);
        return first == null ? "" : first.optString("siteName");
    }

    private static int indexOf(List<Record> records, String playingId) {
        if (StringUtil.isEmpty(playingId)) {
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).selectFlag) {
                    return i;
                }
            }
            return records.isEmpty() ? -1 : 0;
        }
        for (int i = 0; i < records.size(); i++) {
            if (playingId.equals(records.get(i).playingId)) {
                return i;
            }
        }
        return records.isEmpty() ? -1 : 0;
    }

    private static List<Record> rotateFrom(List<Record> records, int start) {
        List<Record> out = new ArrayList<>();
        if (records.isEmpty()) {
            return out;
        }
        int size = records.size();
        int index = start < 0 ? 0 : start;
        for (int i = 0; i < size; i++) {
            out.add(records.get((index + i) % size));
        }
        return out;
    }

    // ═══════════════════════════ 取值工具 ═══════════════════════════

    /** 活动状态: 抓包 data 直接是字符串 "PLAYING"。 */
    private static String playStateOf(MtopSender.Result result) {
        if (result == null || !result.ok) {
            return "";
        }
        JSONObject body = result.body();
        if (body == null) {
            return "";
        }
        Object data = body.opt("data");
        if (data instanceof String) {
            return (String) data;
        }
        String text = body.optString("data");
        if (StringUtil.isEmpty(text)) {
            text = body.optString("playState");
        }
        if (StringUtil.isEmpty(text)) {
            text = body.optString("state");
        }
        return text;
    }

    /** 体力: data.userPointsInfo.points(字符串)。 */
    private static int pointsOf(JSONObject body) {
        if (body == null) {
            return -1;
        }
        JSONObject info = body.optJSONObject("userPointsInfo");
        if (info == null) {
            return -1;
        }
        return toInt(info.optString("points"), -1);
    }

    /** 单次前进消耗的体力: data.userPointsInfo.singleConsumePoints, 缺省 2000(抓包实测)。 */
    private static int singleConsumeOf(JSONObject body) {
        if (body == null) {
            return 2000;
        }
        JSONObject info = body.optJSONObject("userPointsInfo");
        if (info == null) {
            return 2000;
        }
        int unit = toInt(info.optString("singleConsumePoints"), 2000);
        return unit <= 0 ? 2000 : unit;
    }

    private static int toInt(String text, int def) {
        if (StringUtil.isEmpty(text)) {
            return def;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (Throwable ignore) {
            return def;
        }
    }

    private static long toLong(String text, long def) {
        if (StringUtil.isEmpty(text)) {
            return def;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (Throwable ignore) {
            return def;
        }
    }

    private static String percent(double ratio) {
        if (ratio < 0) {
            return "?";
        }
        return String.format(java.util.Locale.CHINA, "%.0f%%", ratio * 100);
    }

    /** 0~1 的随机比例, 用于把前进间隔打散 */
    private static double random() {
        return Math.random();
    }

    private static long randomDelay() {
        return FORWARD_INTERVAL_MIN_MS
                + (long) (random() * (FORWARD_INTERVAL_MAX_MS - FORWARD_INTERVAL_MIN_MS));
    }

    // ═══════════════════════════ 调试落盘 ═══════════════════════════

    private void debug(String label, String text) {
        if (debugDump == null) {
            debugDump = new StringBuilder();
        }
        if (debugDump.length() >= MAX_DEBUG_CHARS) {
            return;
        }
        debugDump.append("\n[").append(label).append("] ").append(truncate(text, 900));
    }

    private void warn(String label, MtopSender.Result result) {
        debugWarnCount++;
        debug("warn:" + label, describeRaw(result));
    }

    private static String describeRaw(MtopSender.Result r) {
        if (r == null) {
            return "null";
        }
        return "ok=" + r.ok + " ret=" + r.retText + " code=" + r.retCode
                + " reason=" + r.failReason + " note=" + r.note
                + " data=" + truncate(String.valueOf(r.data), 700);
    }

    /**
     * 把本轮请求参数/响应/反射落点转储到文件, 便于真机定位(尤其是 wua/asac 是否被宿主自动注入、
     * 以及任务体力领取接口是否可用)。有异常时才在模块日志里提示文件位置, 正常轮次静默落盘。
     */
    private void flushDebug(int totalForward, int switched, int points) {
        if (debugDump == null || debugDump.length() == 0) {
            return;
        }
        try {
            File dir = logDir();
            if (dir == null) {
                return;
            }
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            File file = new File(dir, FLAG_DEBUG_FILE);
            StringBuilder sb = new StringBuilder();
            sb.append("\n===== ")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                    .append(" 体育游乐记 | 前进=").append(totalForward).append(" 切换城市=").append(switched)
                    .append(" 剩余体力=").append(points)
                    .append(" 开关[跑前领体力=").append(autoSupplyPoints.getValue())
                    .append(", 自动领任务体力=").append(autoClaimTaskReward.getValue())
                    .append(", 自动换城市=").append(autoSwitchRecord.getValue())
                    .append(", 单轮上限=").append(maxForwardPerRun.getValue()).append("] =====");
            sb.append(debugDump);
            FileOutputStream fos = new FileOutputStream(file, true);
            try {
                OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
                writer.write(sb.toString());
                writer.flush();
            } finally {
                fos.close();
            }
            if (debugWarnCount > 0) {
                Log.record("体育游乐记: 本轮有 " + debugWarnCount + " 处异常, 排障转储 → " + file.getAbsolutePath());
            }
        } catch (Throwable t) {
            Log.err(TAG, "flushDebug err:", t);
        }
    }

    /** 优先写到模块日志目录, 拿不到时退化到应用私有目录。 */
    private static File logDir() {
        try {
            File dir = FileUtil.LOG_DIRECTORY_FILE;
            if (dir != null) {
                return dir;
            }
        } catch (Throwable ignore) {
        }
        Context ctx = ApplicationHook.getContext();
        if (ctx == null) {
            return null;
        }
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) {
            dir = ctx.getFilesDir();
        }
        return dir;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    // ═══════════════════════════ 内部模型 ═══════════════════════════

    /** 一条城市游玩记录。 */
    private static final class Record {

        final String playingId;
        final String cityName;
        final String siteName;
        /** 0~1, 抓包字段 progress; <0 表示未知 */
        final double progress;
        final long usedPoints;
        final long unlockPoints;
        final boolean selectFlag;

        int drained;
        /** 已走完 / 已达服务端上限 */
        boolean finished;

        Record(String playingId, String cityName, String siteName, double progress, long usedPoints,
               long unlockPoints, boolean selectFlag) {
            this.playingId = playingId;
            this.cityName = cityName;
            this.siteName = siteName;
            this.progress = progress;
            this.usedPoints = usedPoints;
            this.unlockPoints = unlockPoints;
            this.selectFlag = selectFlag;
            this.finished = progress >= 1.0d || (unlockPoints > 0 && usedPoints >= unlockPoints);
        }

        String label() {
            String city = StringUtil.isEmpty(cityName) ? "未知城市" : cityName;
            return StringUtil.isEmpty(siteName) ? city + "(" + playingId + ")"
                    : city + "·" + siteName + "(" + playingId + ")";
        }
    }

    /** 一次 drain 的结果。 */
    private static final class DrainResult {
        int done;
        int balance = -1;
        boolean reachedLimit;
        boolean stopByPoints;
        boolean hardFail;
        boolean recordFinished;
        boolean calibrated;
        String lastState = "";
    }
}
