package com.surexu.sesame.model.task.antAnimalGuess;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.hook.AuthCodeHelper;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.TimeUtil;

public class AntAnimalGuess extends ModelTask {

    private static final String TAG = AntAnimalGuess.class.getSimpleName();
    private static final String DISPLAY_NAME = "动物竞猜";

    // ═══ 项目1 配置 ═══
    private static final String P1_NAME = "动物竞猜";
    private static final String P1_APP_ID = "2021006128683670";
    private static final String P1_HOST = "https://qumaoxianapi.dongwuyouxi.com";
    private static final String P1_MANUAL_TOKEN = "4829142314d54494a0c8363b33bac078";

    // ═══ 项目2 配置 ═══
    private static final String P2_NAME = "动物竞猜";
    private static final String P2_APP_ID = "2021006114686014";
    private static final String P2_HOST = "https://quchongciapi.dongwuyouxi.com";
    private static final String P2_MANUAL_TOKEN = "9c5c1b8700064a788d65749f7e95c79a";

    private BooleanModelField animalGuess;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return DISPLAY_NAME;
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(animalGuess = new BooleanModelField("animalGuess", "动物竞猜 | 自动签到",true));
        return modelFields;
    }

    @Override
    public Boolean check() {
        return true;
    }

    @Override
    public void run() {
        try {
            if (animalGuess.getValue()) {
                // 项目1
                String p1Token = initToken(P1_NAME, P1_APP_ID, P1_HOST, P1_MANUAL_TOKEN);
                if (p1Token != null) {
                    doCheckin(P1_NAME, P1_HOST, p1Token);
                    TimeUtil.sleep(600);
                    reportRace(P1_NAME, P1_HOST, p1Token);
                }
                // 项目2
                String p2Token = initToken(P2_NAME, P2_APP_ID, P2_HOST, P2_MANUAL_TOKEN);
                if (p2Token != null) {
                    doCheckin(P2_NAME, P2_HOST, p2Token);
                    TimeUtil.sleep(600);
                    reportRace(P2_NAME, P2_HOST, p2Token);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, DISPLAY_NAME + " start.run err:", t);
        }
    }

    /**
     * 初始化登录态：优先动态登录换 token，失败回退手动 token
     */
    private String initToken(String name, String appId, String host, String manualToken) {
        String token = dynamicLogin(name, appId, host);
        if (token != null) {
            JSONObject res = requestApi(host, "/app-api/member/user/get", "GET", null, token);
            if (res != null && res.optInt("code") == 0 && res.optJSONObject("data") != null) {
                JSONObject data = res.optJSONObject("data");
                Log.other(DISPLAY_NAME + "[" + name + "]登录态有效: " + data.optString("nickname", "") + "，当前积分: " + data.optInt("point"));
                return token;
            }
            Log.other(DISPLAY_NAME + "[" + name + "]动态登录校验失败，回退手动token");
        }

        if (manualToken == null || manualToken.isEmpty()) {
            Log.other(DISPLAY_NAME + "[" + name + "]未配置 token 且环境不支持动态登录");
            return null;
        }
        token = manualToken;
        JSONObject res = requestApi(host, "/app-api/member/user/get", "GET", null, token);
        if (res != null && res.optInt("code") == 0 && res.optJSONObject("data") != null) {
            JSONObject data = res.optJSONObject("data");
            Log.other(DISPLAY_NAME + "[" + name + "]已使用手动token: " + data.optString("nickname", "") + "，积分 " + data.optInt("point"));
            return token;
        }
        Log.other(DISPLAY_NAME + "[" + name + "]token 已失效，请重新抓包替换");
        return null;
    }

    /**
     * 动态登录：AuthCodeHelper.getAuthCode 获取授权码换 accessToken
     */
    private String dynamicLogin(String name, String appId, String host) {
        String loginCode = AuthCodeHelper.getAuthCode(appId);
        if (loginCode == null || loginCode.isEmpty()) {
            Log.other(DISPLAY_NAME + "[" + name + "]获取授权码失败");
            return null;
        }
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("loginCode", loginCode);
            bodyJson.put("state", 1);
            String resStr = requestRaw(host, "/app-api/member/auth/zfb-mini-app-login", "POST", bodyJson.toString(), null);
            if (resStr == null) {
                return null;
            }
            JSONObject res = new JSONObject(resStr);
            if (res.optInt("code") == 0 && res.optJSONObject("data") != null && res.optJSONObject("data").has("accessToken")) {
                Log.other(DISPLAY_NAME + "[" + name + "]动态登录成功(当前账号自动适配)");
                return res.optJSONObject("data").optString("accessToken");
            } else {
                Log.other(DISPLAY_NAME + "[" + name + "]动态登录失败: " + res.optString("msg", "未知错误"));
                return null;
            }
        } catch (Throwable t) {
            Log.err(TAG, DISPLAY_NAME + "[" + name + "]动态登录异常:", t);
            return null;
        }
    }

    /**
     * 签到流程：查配置 → 创建签到 → 查积分（每天仅签到一次：status=0 表示今天未签，无则跳过）
     */
    private void doCheckin(String name, String host, String token) {
        try {
            JSONObject cfgRes = requestApi(host, "/app-api/member/sign-in/config/list", "GET", null, token);
            JSONArray cfgList = cfgRes != null ? cfgRes.optJSONArray("data") : null;
            if (cfgList == null || cfgList.length() == 0) {
                Log.other(DISPLAY_NAME + "[" + name + "]未获取到签到配置");
                return;
            }
            int progress = 0;
            JSONObject todayCfg = null;
            for (int i = 0; i < cfgList.length(); i++) {
                JSONObject cfg = cfgList.optJSONObject(i);
                if (cfg == null) {
                    continue;
                }
                if (cfg.optInt("status") == 0 && todayCfg == null) {
                    todayCfg = cfg;
                } else if (cfg.optInt("status") == 1) {
                    progress++;
                }
            }
            if (todayCfg == null) {
                Log.other(DISPLAY_NAME + "[" + name + "]今天已签到，跳过");
                return;
            }
            Log.other(DISPLAY_NAME + "[" + name + "]本周签到进度: 已签" + progress + "/7天，今天可领 " + todayCfg.optString("point") + " 积分");

            JSONObject signRes = requestApi(host, "/app-api/member/sign-in/record/create", "POST", new JSONObject(), token);
            if (signRes != null && signRes.optInt("code") == 0) {
                Log.other(DISPLAY_NAME + "[" + name + "]签到成功");
            } else {
                Log.other(DISPLAY_NAME + "[" + name + "]签到结果: " + (signRes != null ? signRes.optString("msg", "未知") : "未知"));
            }

            JSONObject userRes = requestApi(host, "/app-api/member/user/get", "GET", null, token);
            if (userRes != null && userRes.optInt("code") == 0 && userRes.optJSONObject("data") != null) {
                Log.other(DISPLAY_NAME + "[" + name + "]当前账户积分: " + userRes.optJSONObject("data").optInt("point"));
            }
        } catch (Throwable t) {
            Log.err(TAG, DISPLAY_NAME + "[" + name + "]签到异常:", t);
        }
    }

    /**
     * 查询当前竞猜局
     */
    private void reportRace(String name, String host, String token) {
        try {
            JSONObject raceRes = requestApi(host, "/app-api/member/guess-race/get", "GET", null, token);
            JSONObject raceData = raceRes != null ? raceRes.optJSONObject("data") : null;
            if (raceData != null) {
                Log.other(DISPLAY_NAME + "[" + name + "]当前竞猜局: " + raceData.optString("raceNumber") + "，赛程 " + raceData.optString("startTime") + " ~ " + raceData.optString("endTime"));
            }
        } catch (Throwable t) {
            Log.err(TAG, DISPLAY_NAME + "[" + name + "]查询竞猜异常:", t);
        }
    }

    /**
     * JSON 请求（自动解析响应）
     */
    private JSONObject requestApi(String host, String path, String method, JSONObject data, String token) {
        String resStr = requestRaw(host, path, method, data != null ? data.toString() : "", token);
        if (resStr == null) {
            return null;
        }
        try {
            return new JSONObject(resStr);
        } catch (Throwable t) {
            Log.err(TAG, DISPLAY_NAME + " 响应解析失败:", t);
            return null;
        }
    }

    /**
     * 原始 HTTP 请求
     */
    private String requestRaw(String host, String path, String method, String body, String token) {
        try {
            URL url = new URL(host + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method.toUpperCase());
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("content-type", "application/json");
            conn.setRequestProperty("x-release-type", "ONLINE");
            conn.setRequestProperty("Accept-Charset", "UTF-8");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("authorization", "Bearer " + token);
            }
            if ("POST".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(body == null ? "" : body);
                }
            }
            int respCode = conn.getResponseCode();
            StringBuilder responseText = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseText.append(line);
                }
            } finally {
                conn.disconnect();
            }
            return responseText.toString();
        } catch (IOException e) {
            Log.err(TAG, DISPLAY_NAME + " 请求网络异常(" + host + path + "):", e);
            return null;
        } catch (Throwable t) {
            Log.err(TAG, DISPLAY_NAME + " 请求异常(" + host + path + "):", t);
            return null;
        }
    }
}
