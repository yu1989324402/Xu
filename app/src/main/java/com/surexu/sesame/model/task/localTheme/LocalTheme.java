package com.surexu.sesame.model.task.localTheme;

import android.content.Context;
import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.RuntimeInfo;
import com.surexu.sesame.data.modelFieldExt.ChoiceModelField;
import com.surexu.sesame.data.modelFieldExt.StringModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.hook.ApplicationHook;
import com.surexu.sesame.util.ClassUtil;
import com.surexu.sesame.util.FileUtil;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.idMap.UserIdMap;
import java.io.File;

public final class LocalTheme extends ModelTask {
    private ChoiceModelField action;
    private StringModelField operationId;
    private StringModelField themeId;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "本地主题替换";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        ChoiceModelField choiceModelField = new ChoiceModelField("action", "操作", 0, new String[]{"仅预览", "应用本地主题", "恢复备份"});
        this.action = choiceModelField;
        modelFields.addField(choiceModelField);
        StringModelField stringModelField = new StringModelField("themeId", "已下载主题ID", "");
        this.themeId = stringModelField;
        modelFields.addField(stringModelField);
        StringModelField stringModelField2 = new StringModelField("operationId", "执行编号(修改后执行一次)", "");
        this.operationId = stringModelField2;
        modelFields.addField(stringModelField2);
        return modelFields;
    }

    private String operationKey() {
        return this.action.getValue() + ":" + this.themeId.getValue() + ":" + this.operationId.getValue();
    }

    @Override
    public Boolean check() {
        String currentUid = UserIdMap.getCurrentUid();
        return Boolean.valueOf((!isEnable().booleanValue() || currentUid == null || !currentUid.matches("[0-9]{1,32}") || this.themeId.getValue().isEmpty() || this.operationId.getValue().trim().isEmpty() || operationKey().equals(RuntimeInfo.getInstance().getString("LocalTheme.lastAttempt"))) ? false : true);
    }

    @Override
    public synchronized void run() {
        if (check().booleanValue()) {
            final String currentUid = UserIdMap.getCurrentUid();
            String value = this.themeId.getValue();
            int iIntValue = this.action.getValue().intValue();
            final String strOperationKey = operationKey();
            RuntimeInfo.getInstance().put("LocalTheme.lastAttempt", strOperationKey);
            Context context = ApplicationHook.getContext();
            if (context != null && ClassUtil.PACKAGE_NAME.equals(context.getPackageName())) {
                ThemeArchiveInstaller.Validity validity = new ThemeArchiveInstaller.Validity() {
                    @Override
                    public final boolean isCurrent() {
                        return LocalTheme.this.isEnable().booleanValue() && currentUid.equals(UserIdMap.getCurrentUid()) && strOperationKey.equals(LocalTheme.this.operationKey()) && !Thread.currentThread().isInterrupted();
                    }
                };
                if (validity.isCurrent()) {
                    File file = new File(FileUtil.MAIN_DIRECTORY_FILE, "themes/skin.zip");
                    try {
                        File file2 = new File(context.getFilesDir().getCanonicalFile(), "skin_center_dir/" + currentUid + "/theme");
                        if (iIntValue == 2) {
                            ThemeArchiveInstaller.restore(file2, value, validity);
                            Log.record("本地主题替换：原始资源已恢复，重新进入主题页面查看");
                        } else if (iIntValue == 1) {
                            Log.record("本地主题替换：已备份并替换资源数=" + ThemeArchiveInstaller.apply(file2, value, file, validity).replacements + "，重新进入主题页面查看");
                        } else {
                            ThemeArchiveInstaller.Preview preview = ThemeArchiveInstaller.preview(file2, value, file);
                            Log.record("本地主题替换预览：可替换资源数=" + preview.replacements + "，解压总字节=" + preview.bytes);
                        }
                    } catch (Exception e) {
                        Log.record("本地主题替换未完成：" + e.getClass().getSimpleName() + "，检查主题ID、ZIP结构或备份；修改执行编号后重试");
                    }
                }
            }
        }
    }
}
