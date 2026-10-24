package com.surexu.sesame.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.model.base.ModelOrder;
import com.surexu.sesame.util.Log;
import lombok.Getter;

public abstract class Model {

    private static final Map<String, ModelConfig> modelConfigMap = new LinkedHashMap<>();

    private static final Map<String, ModelConfig> readOnlyModelConfigMap = Collections.unmodifiableMap(modelConfigMap);

    private static final Map<ModelGroup, Map<String, ModelConfig>> groupModelConfigMap = new LinkedHashMap<>();

    private static final Map<Class<? extends Model>, Model> modelMap = new ConcurrentHashMap<>();

    private static final List<Class<? extends Model>> modelClazzList = ModelOrder.getClazzList();

    @Getter
    private static final Model[] modelArray = new Model[modelClazzList.size()];

    private static final List<Model> modelList = Arrays.asList(modelArray);

    private static final List<Model> readOnlyModelList = Collections.unmodifiableList(modelList);

    private final BooleanModelField enableField;

    public final BooleanModelField getEnableField() {
        return enableField;
    }

    public Model() {
        this.enableField = new BooleanModelField("enable", getEnableFieldName(), getDefaultEnable());
    }

    public String getEnableFieldName() {
        return "开启" + getName();
    }

    /**
     * 该功能是否"出厂默认开启"。默认 false（模块一贯的全部默认关闭）；
     * 希望重装后无需手动打开的功能，重写本方法返回 true 即可。
     * 注意：构造期调用，实现里不要依赖子类尚未初始化的字段。
     */
    public Boolean getDefaultEnable() {
        return false;
    }

    public final Boolean isEnable() {
        return enableField.getValue();
    }

    public ModelType getType() {
        return ModelType.NORMAL;
    }

    public abstract String getName();

    public abstract ModelGroup getGroup();

    public abstract ModelFields getFields();

    public void prepare() {}

    public void boot(ClassLoader classLoader) {}

    public void destroy() {}

    public static Map<String, ModelConfig> getModelConfigMap() {
        return readOnlyModelConfigMap;
    }

    public static Set<ModelGroup> getGroupModelConfigGroupSet() {
        return groupModelConfigMap.keySet();
    }

    public static List<Map<String, ModelConfig>> getGroupModelConfigMapList() {
        List<Map<String, ModelConfig>> list = new ArrayList<>();
        for (Map<String, ModelConfig> modelConfigMap : groupModelConfigMap.values()) {
            list.add(Collections.unmodifiableMap(modelConfigMap));
        }
        return list;
    }

    public static Map<String, ModelConfig> getGroupModelConfig(ModelGroup modelGroup) {
        Map<String, ModelConfig> map = groupModelConfigMap.get(modelGroup);
        if (map == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(map);
    }

    public static Boolean hasModel(Class<? extends Model> modelClazz) {
        return modelMap.containsKey(modelClazz);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Model> T getModel(Class<T> modelClazz) {
        return (T) modelMap.get(modelClazz);
    }

    public static List<Model> getModelList() {
        return readOnlyModelList;
    }

    public static synchronized void initAllModel() {
        // 幂等：进程内 Model 已初始化过就复用，禁止反复销毁重建。
        // 配置页各 Activity（设置页/分组字段页/选择编辑页）的 onCreate 都会调用本方法，若每次都重建字段对象，
        // 页面栈中前一页 UI 已持有的 ModelField 引用会被丢弃成孤儿对象（例如 分组字段页 -> 选择编辑页 跳转后
        // 返回，分组页 remember 缓存的 field 仍是旧引用），返回后继续修改会写进孤儿对象，
        // ConfigV2.save() 的 collectChangedFields 遍历不到这些改动 -> isModify=false -> 保存静默跳过，
        // 表现为"有些配置配置完了返回还是没保存"（典型触发：点过选择类字段进入编辑页后返回再改其他项）。
        // ApplicationHook 每次注入支付宝进程都是新进程（modelMap 为空），仍会走下方全量重建，不受影响。
        if (!modelMap.isEmpty()) {
            return;
        }
        destroyAllModel();
        for (int i = 0, len = modelClazzList.size(); i < len; i++) {
            Class<? extends Model> modelClazz = modelClazzList.get(i);
            try {
                Model model = modelClazz.getDeclaredConstructor().newInstance();
                ModelConfig modelConfig = new ModelConfig(model);
                modelArray[i] = model;
                modelMap.put(modelClazz, model);
                String modelCode = modelConfig.getCode();
                modelConfigMap.put(modelCode, modelConfig);
                ModelGroup group = modelConfig.getGroup();
                Map<String, ModelConfig> modelConfigMap = groupModelConfigMap.get(group);
                if (modelConfigMap == null) {
                    modelConfigMap = new LinkedHashMap<>();
                    groupModelConfigMap.put(group, modelConfigMap);
                }
                modelConfigMap.put(modelCode, modelConfig);
            } catch (ReflectiveOperationException e) {
                Log.printStackTrace(e);
            }
        }
        // 重建 Model 实例后，把 ConfigV2 单例持有的字段引用同步到新建的 ModelConfig 字段上：
        // 配置页各 Activity 的 onCreate 都会调用本方法重建字段对象（enable 等字段回到构造默认值），
        // 而 ConfigV2.INSTANCE.isInit() 置 true 后不再重新 load，若不同步会出现 UI 读新字段、
        // save() 写旧引用的错位，表现为"开关打开保存后返回又自动关闭"。
        try {
            java.util.Map<String, ModelFields> snapshot = ConfigV2.INSTANCE.getModelFieldsMap();
            if (snapshot == null || snapshot.isEmpty()) {
                // 配置还没加载（或刚被 clear/unload）：把空快照同步过去会让所有字段退回代码默认值
                // （默认值几乎全是关闭），表现就是「配置全没了、模块自己关了」。
                // 保持刚重建的字段不动，等真正的 load 把值填进来。
                Log.record("配置尚未加载，跳过重建字段的值同步");
            } else {
                // 注意：setModelFieldsMap 内部会先 clear 传入的 map，必须传副本，否则旧值全丢
                ConfigV2.INSTANCE.setModelFieldsMap(new java.util.HashMap<>(snapshot));
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }


    public static synchronized void bootAllModel(ClassLoader classLoader) {
        // 配置没加载成功时不允许按「默认值」启动任务：默认值几乎全关，启动只会跑出一堆无效请求，
        // 而用户看到的是「功能全被关了」。等配置加载好再启动。
        if (!ConfigV2.INSTANCE.isInit()) {
            Log.record("配置尚未加载，跳过全部任务启动");
            return;
        }
        for (Model model : modelArray) {

            try {
                model.prepare();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
            try {
                if (model.getEnableField().getValue()) {
                    model.boot(classLoader);
                }
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
    }

    public static synchronized void destroyAllModel() {
        for (int i = 0, len = modelArray.length; i < len; i++) {
            Model model = modelArray[i];
            if (model != null) {
                try {
                    if (ModelType.TASK == model.getType()) {
                        ((ModelTask) model).stopTask();
                    }
                    model.destroy();
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
                modelArray[i] = null;
            }
            modelMap.clear();
            modelConfigMap.clear();
        }
    }

}
