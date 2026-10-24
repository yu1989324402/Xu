package com.surexu.sesame.model.task.rewardSupport;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.RuntimeInfo;
import com.surexu.sesame.data.modelFieldExt.IntegerModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.hook.ApplicationHook;
import com.surexu.sesame.model.base.TaskCommon;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.idMap.UserIdMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONObject;

public abstract class IsolatedRewardTask extends ModelTask {
    private static final ReentrantLock RUN_LOCK = new ReentrantLock();
    private IntegerModelField intervalHours;

    public interface Allowed {
        boolean isAllowed();
    }

    protected abstract void addFields(ModelFields modelFields);

    protected abstract void execute(Run run) throws Exception;

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public final ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        IntegerModelField integerModelField = new IntegerModelField("intervalHours", "查询间隔(小时)", 6, 1, 24);
        this.intervalHours = integerModelField;
        modelFields.addField(integerModelField);
        addFields(modelFields);
        return modelFields;
    }

    public String nextKey() {
        return getClass().getSimpleName() + ".nextQuery";
    }

    @Override
    public final Boolean check() {
        String currentUid = UserIdMap.getCurrentUid();
        return Boolean.valueOf(isEnable().booleanValue() && RewardRunPolicy.sameAccount(currentUid, currentUid) && !ApplicationHook.isOffline() && !TaskCommon.IS_ENERGY_TIME.booleanValue() && RewardRunPolicy.mayQuery(System.currentTimeMillis(), RuntimeInfo.getInstance().getLong(nextKey(), 0L).longValue()));
    }

    @Override
    public final void run() {
        String currentUid = UserIdMap.getCurrentUid();
        boolean z = false;
        try {
            try {
                try {
                    try {
                        ReentrantLock reentrantLock = RUN_LOCK;
                        reentrantLock.lockInterruptibly();
                        z = true;
                        try {
                            if (RewardRunPolicy.sameAccount(currentUid, UserIdMap.getCurrentUid()) && check().booleanValue()) {
                                Run run = new Run(currentUid, RuntimeInfo.getInstance());
                                run.requireCurrent();
                                run.state.put(nextKey(), Long.valueOf(System.currentTimeMillis() + (((long) this.intervalHours.getValue().intValue()) * 3600000)));
                                execute(run);
                                reentrantLock.unlock();
                                return;
                            }
                            reentrantLock.unlock();
                        } catch (Stopped unused) {
                            RUN_LOCK.unlock();
                        }
                    } catch (InterruptedException unused2) {
                        Thread.currentThread().interrupt();
                        if (z) {
                            RUN_LOCK.unlock();
                        }
                    }
                } catch (Exception e) {
                    Log.record(getName() + "：本轮结束，异常类型=" + e.getClass().getSimpleName());
                    if (z) {
                        RUN_LOCK.unlock();
                    }
                }
            } catch (Throwable th) {
                if (z) {
                    RUN_LOCK.unlock();
                }
                throw th;
            }
        } catch (Exception unused3) {
        }
    }

    protected final class Run {
        private final String account;
        private long lastCallNanos;
        private int requests;
        private final RuntimeInfo state;

        static boolean lambda$query$0() {
            return true;
        }

        private Run(String str, RuntimeInfo runtimeInfo) {
            this.account = str;
            this.state = runtimeInfo;
        }

        public void requireCurrent() throws Stopped {
            if (Thread.currentThread().isInterrupted() || !IsolatedRewardTask.this.isEnable().booleanValue() || ApplicationHook.isOffline() || !RewardRunPolicy.sameAccount(this.account, UserIdMap.getCurrentUid())) {
                throw new Stopped();
            }
        }

        public JSONObject query(String str, String str2) throws Exception {
            return call(str, str2, new Allowed() {
                @Override
                public final boolean isAllowed() {
                    return IsolatedRewardTask.Run.lambda$query$0();
                }
            });
        }

        private JSONObject call(String str, String str2, Allowed allowed) throws Exception {
            requireCurrent();
            if (!allowed.isAllowed()) {
                throw new Stopped();
            }
            // 月月赚等任务需要在一轮内完成多个浏览任务（每个占 2 次 RPC），
            // 故放宽硬上限；各子类仍有自身 budget 约束，不会无限循环。
            if (this.requests >= 40) {
                throw new Stopped();
            }
            if (this.lastCallNanos != 0) {
                long jNanoTime = 1500 - ((System.nanoTime() - this.lastCallNanos) / 1000000);
                if (jNanoTime > 0) {
                    Thread.sleep(jNanoTime);
                }
            }
            requireCurrent();
            if (!allowed.isAllowed()) {
                throw new Stopped();
            }
            this.requests++;
            try {
                String strRequestString = ApplicationHook.requestString(str, str2, 1, -1);
                this.lastCallNanos = System.nanoTime();
                requireCurrent();
                if (strRequestString == null || strRequestString.isEmpty()) {
                    Log.record(IsolatedRewardTask.this.getName() + "：无响应，停止本轮 method=" + str);
                    throw new Stopped();
                }
                JSONObject jSONObject = new JSONObject(strRequestString);
                if (jSONObject.optInt("error", 0) == 1009 || "访问被拒绝".equals(jSONObject.optString("errorMessage"))) {
                    this.state.put(IsolatedRewardTask.this.nextKey(), Long.valueOf(System.currentTimeMillis() + 86400000));
                    Log.record(IsolatedRewardTask.this.getName() + "：访问被拒绝，暂停24小时 method=" + str);
                    throw new Stopped();
                }
                if (jSONObject.optInt("error", 0) == 0 && Boolean.TRUE.equals(jSONObject.opt("success"))) {
                    return jSONObject;
                }
                Log.record(IsolatedRewardTask.this.getName() + "：响应未明确成功，停止本轮 method=" + str);
                throw new Stopped();
            } catch (Throwable th) {
                this.lastCallNanos = System.nanoTime();
                throw th;
            }
        }

        public JSONObject onceToday(String str, String str2, String str3, Allowed allowed) throws Exception {
            requireCurrent();
            if (!allowed.isAllowed()) {
                throw new Stopped();
            }
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            String str4 = simpleDateFormat.format(new Date());
            String str5 = getClassKey() + ".attempt." + str;
            if (!RewardRunPolicy.mayAttempt(str4, this.state.getString(str5))) {
                Log.record(IsolatedRewardTask.this.getName() + "：今日已尝试该动作，等待下一日");
                return null;
            }
            this.state.put(str5, str4);
            return call(str2, str3, allowed);
        }

        private String getClassKey() {
            return IsolatedRewardTask.this.getClass().getSimpleName();
        }
    }

    private static final class Stopped extends Exception {
        private Stopped() {
        }
    }
}
