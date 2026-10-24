package com.surexu.sesame.model.task.other;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class OtherTask extends ModelTask {
    private static final String TAG = "OtherTask";
    private BooleanModelField haojiaWuyou;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "其他任务";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        BooleanModelField booleanModelField2 = new BooleanModelField("haojiaWuyou", "好家无忧卡",true);
        this.haojiaWuyou = booleanModelField2;
        modelFields.addField(booleanModelField2);
        return modelFields;
    }

    @Override
    public Boolean check() {
        return true;
    }

    @Override
    public void run() {
        try {
            if (this.haojiaWuyou.getValue().booleanValue()) {
                runHaoJia();
            }
        } catch (Throwable th) {
            Log.i(TAG, "其他任务执行异常");
            Log.printStackTrace(TAG, th);
        }
    }


    private void runHaoJia() {
        Log.record("好家无忧卡开始执行");
        try {
            JSONObject jSONObject = new JSONObject(HaoJiaRpcCall.querySignIn());
            JSONArray jSONArrayOptJSONArray = null;
            if (ok(jSONObject)) {
                JSONObject jSONObjectComponent = component(jSONObject, "independent_component_sign_in_00966139_independent_component_sign_in_recall");
                JSONObject jSONObjectOptJSONObject = jSONObjectComponent == null ? null : jSONObjectComponent.optJSONObject("content");
                JSONArray jSONArrayOptJSONArray2 = jSONObjectOptJSONObject == null ? null : jSONObjectOptJSONObject.optJSONArray("playSignInOrderInfoList");
                if (jSONArrayOptJSONArray2 != null && jSONArrayOptJSONArray2.length() > 0) {
                    JSONObject jSONObjectOptJSONObject2 = jSONArrayOptJSONArray2.optJSONObject(0);
                    JSONObject jSONObjectOptJSONObject3 = jSONObjectOptJSONObject2 == null ? null : jSONObjectOptJSONObject2.optJSONObject("playSignInTemplateInfo");
                    String strOptString = jSONObjectOptJSONObject3 == null ? "" : jSONObjectOptJSONObject3.optString("code");
                    JSONArray jSONArrayOptJSONArray3 = jSONObjectOptJSONObject2 == null ? null : jSONObjectOptJSONObject2.optJSONArray("signInRecordInfoList");
                    if (!strOptString.isEmpty() && !hasSignedToday(jSONArrayOptJSONArray3) && ok(new JSONObject(HaoJiaRpcCall.doSignIn(strOptString)))) {
                        Log.record("好家无忧卡签到完成");
                    }
                }
            }
            JSONObject jSONObjectComponent2 = component(new JSONObject(HaoJiaRpcCall.queryTaskList()), "independent_component_task_reward_00793835_independent_component_task_reward_query");
            JSONObject jSONObjectOptJSONObject4 = jSONObjectComponent2 == null ? null : jSONObjectComponent2.optJSONObject("content");
            if (jSONObjectOptJSONObject4 != null) {
                jSONArrayOptJSONArray = jSONObjectOptJSONObject4.optJSONArray("playTaskOrderInfoList");
            }
            if (jSONArrayOptJSONArray != null) {
                for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                    JSONObject jSONObjectOptJSONObject5 = jSONArrayOptJSONArray.optJSONObject(i);
                    if (jSONObjectOptJSONObject5 != null && "init".equals(jSONObjectOptJSONObject5.optString("taskStatus")) && !"eventPush".equals(jSONObjectOptJSONObject5.optString("advanceType"))) {
                        JSONObject jSONObjectOptJSONObject6 = jSONObjectOptJSONObject5.optJSONObject("displayInfo");
                        String strOptString2 = jSONObjectOptJSONObject6 == null ? "" : jSONObjectOptJSONObject6.optString("activityName");
                        if (!containsRisk(strOptString2)) {
                            String strOptString3 = jSONObjectOptJSONObject5.optString("code");
                            int iOptInt = jSONObjectOptJSONObject6 == null ? 0 : jSONObjectOptJSONObject6.optInt("browseTime", 0);
                            if (iOptInt > 0) {
                                sleep(((long) iOptInt) * 1000);
                            }
                            if (!strOptString3.isEmpty() && ok(new JSONObject(HaoJiaRpcCall.applyTask(strOptString3)))) {
                                Log.record("好家无忧卡完成任务 " + strOptString2);
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "好家无忧卡执行异常");
            Log.printStackTrace(TAG, th);
        }
    }

    private static JSONObject component(JSONObject jSONObject, String str) {
        JSONObject jSONObjectOptJSONObject = jSONObject == null ? null : jSONObject.optJSONObject("components");
        if (jSONObjectOptJSONObject == null) {
            return null;
        }
        return jSONObjectOptJSONObject.optJSONObject(str);
    }

    private static boolean containsRisk(String str) {
        return str.contains("流量") || str.contains("话费") || str.contains("理财") || str.contains("保险") || str.contains("购车") || str.contains("开通") || str.contains("办理") || str.contains("咨询") || str.contains("黄金");
    }

    private static boolean hasSignedToday(JSONArray jSONArray) {
        String str = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        for (int i = 0; jSONArray != null && i < jSONArray.length(); i++) {
            JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject != null && str.equals(jSONObjectOptJSONObject.optString("date").replaceAll("[^0-9]", ""))) {
                return true;
            }
        }
        return false;
    }

    private static boolean ok(JSONObject jSONObject) {
        return jSONObject != null && (jSONObject.optBoolean("success") || jSONObject.optBoolean("isSuccess") || "SUCCESS".equalsIgnoreCase(jSONObject.optString("resultCode")) || "200".equals(jSONObject.optString("resultCode")) || "处理成功".equals(jSONObject.optString("desc")));
    }

    private static void sleep(long j) {
        try {
            Thread.sleep(j);
        } catch (InterruptedException unused) {
            Thread.currentThread().interrupt();
        }
    }
}
