package com.surexu.sesame.model.task.healthIslandRewards;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.modelFieldExt.IntegerModelField;
import com.surexu.sesame.data.modelFieldExt.StringModelField;
import com.surexu.sesame.model.task.rewardSupport.IsolatedRewardTask;
import com.surexu.sesame.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HealthIslandRewards extends IsolatedRewardTask {
    private static final String PREFIX = "com.alipay.neverland.biz.rpc.";
    private BooleanModelField exchange;
    private IntegerModelField maxPieces;
    private StringModelField prizeId;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "健康岛红包碎片兑换";
    }

    @Override
    protected void addFields(ModelFields modelFields) {
        BooleanModelField booleanModelField = new BooleanModelField("exchange", "兑换即将到期的碎片",true);
        this.exchange = booleanModelField;
        modelFields.addField(booleanModelField);
        StringModelField stringModelField = new StringModelField("prizeId", "指定兑换礼品ID", "");
        this.prizeId = stringModelField;
        modelFields.addField(stringModelField);
        IntegerModelField integerModelField = new IntegerModelField("maxPieces", "单次最多消耗碎片", 500, 1, 100000);
        this.maxPieces = integerModelField;
        modelFields.addField(integerModelField);
    }

    @Override
    protected void execute(IsolatedRewardTask.Run run) throws Exception {
        JSONObject jSONObjectOptJSONObject = run.query("com.alipay.neverland.biz.rpc.queryExchangeModule", "[{\"assetType\":\"RED_PACKAGE_PIECE\",\"source\":\"jkddicon\"}]").optJSONObject("data");
        JSONObject jSONObject = null;
        JSONObject jSONObjectOptJSONObject2 = jSONObjectOptJSONObject == null ? null : jSONObjectOptJSONObject.optJSONObject("mediumModule");
        JSONObject jSONObjectOptJSONObject3 = jSONObjectOptJSONObject == null ? null : jSONObjectOptJSONObject.optJSONObject("exchangePrizeModule");
        if (jSONObjectOptJSONObject2 == null || jSONObjectOptJSONObject3 == null) {
            Log.record(getName() + "：未返回完整兑换信息");
            return;
        }
        String strOptString = jSONObjectOptJSONObject2.optString("expiringAmount", "");
        if (!strOptString.matches("[0-9]{1,9}")) {
            Log.record(getName() + "：到期碎片数量格式不明确");
            return;
        }
        long j = Long.parseLong(strOptString);
        JSONArray jSONArrayOptJSONArray = jSONObjectOptJSONObject3.optJSONArray("exchangePrizes");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() > 100) {
            Log.record(getName() + "：兑换目录格式不明确");
            return;
        }
        if (j == 0) {
            Log.record(getName() + "：没有即将到期的红包碎片");
            return;
        }
        if (!this.exchange.getValue().booleanValue() || this.prizeId.getValue().isEmpty()) {
            Log.record(getName() + "：有到期碎片，兑换未开启或尚未选择礼品；目录条目=" + jSONArrayOptJSONArray.length());
            return;
        }
        final String value = this.prizeId.getValue();
        if (value.length() > 128) {
            return;
        }
        for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
            JSONObject jSONObjectOptJSONObject4 = jSONArrayOptJSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject4 != null && value.equals(jSONObjectOptJSONObject4.optString("prizeId"))) {
                if (jSONObject != null) {
                    Log.record(getName() + "：礼品ID重复，停止本轮");
                    return;
                }
                jSONObject = jSONObjectOptJSONObject4;
            }
        }
        if (jSONObject == null) {
            Log.record(getName() + "：指定礼品不在当前目录");
            return;
        }
        String strOptString2 = jSONObject.optString("statusCode", "");
        String strOptString3 = jSONObject.optString("consumeMediumAmount", "");
        if (strOptString2.isEmpty() || "POINT_NOT_ENOUGH".equals(strOptString2) || !strOptString3.matches("[0-9]{1,9}")) {
            Log.record(getName() + "：礼品数量或状态不满足兑换条件");
            return;
        }
        final long j2 = Long.parseLong(strOptString3);
        if (j2 <= 0 || j2 > j || j2 > this.maxPieces.getValue().intValue()) {
            Log.record(getName() + "：兑换消耗超出到期碎片或配置上限");
            return;
        }
        String strOptString4 = jSONObjectOptJSONObject3.optString("campId", "");
        if (strOptString4.isEmpty() || strOptString4.length() > 128 || !this.exchange.getValue().booleanValue() || !value.equals(this.prizeId.getValue()) || run.onceToday("exchangePieces", "com.alipay.neverland.biz.rpc.doMediumExchangePrize", new JSONArray().put(new JSONObject().put("assetType", "RED_PACKAGE_PIECE").put("prizeId", value).put("campId", strOptString4).put("source", "jkddicon")).toString(), new IsolatedRewardTask.Allowed() {
            @Override
            public final boolean isAllowed() {
                return HealthIslandRewards.this.exchange.getValue().booleanValue() && value.equals(HealthIslandRewards.this.prizeId.getValue()) && j2 <= ((long) HealthIslandRewards.this.maxPieces.getValue().intValue());
            }
        }) == null) {
            return;
        }
        Log.record(getName() + "：兑换接口返回成功，请核对官方奖励记录");
    }
}
