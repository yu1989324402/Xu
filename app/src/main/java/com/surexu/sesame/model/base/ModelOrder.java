package com.surexu.sesame.model.base;

import java.util.ArrayList;
import java.util.List;

import com.surexu.sesame.data.Model;
import com.surexu.sesame.model.extensions.ExtensionsHandle;
import com.surexu.sesame.model.normal.answerAI.AnswerAI;
import com.surexu.sesame.model.normal.base.BaseModel;
import com.surexu.sesame.model.task.ancientTree.AncientTree;
import com.surexu.sesame.model.task.antAnimalGuess.AntAnimalGuess;
import com.surexu.sesame.model.task.plantingFlowers.PlantingFlowers;
import com.surexu.sesame.model.task.antDodo.AntDodo;
import com.surexu.sesame.model.task.antFarm.AntFarm;
import com.surexu.sesame.model.task.antForest.AntForestV2;
import com.surexu.sesame.model.task.antMember.AntMember;
import com.surexu.sesame.model.task.antOcean.AntOcean;
import com.surexu.sesame.model.task.antOrchard.AntOrchard;
import com.surexu.sesame.model.task.consumeGold.ConsumeGold;
import com.surexu.sesame.model.task.antSports.AntSports;
import com.surexu.sesame.model.task.antStall.AntStall;
import com.surexu.sesame.model.task.dailyCash.DailyCash;
import com.surexu.sesame.model.task.dayDaySave.DayDaySave;
import com.surexu.sesame.model.task.greenFinance.GreenFinance;
import com.surexu.sesame.model.task.goldenbeans.goldenbeans;
import com.surexu.sesame.model.task.healthIslandRewards.HealthIslandRewards;
import com.surexu.sesame.model.task.localTheme.LocalTheme;
import com.surexu.sesame.model.task.luckCard.LuckCardStatus;
import com.surexu.sesame.model.task.other.OtherTask;
import com.surexu.sesame.model.task.promoprodRewards.PromoprodRewards;
import com.surexu.sesame.model.task.protectEcology.ProtectEcology;
import com.surexu.sesame.model.task.sesameDog.SesameDog;
import com.surexu.sesame.model.task.sesameSports.SesameSports;
import com.surexu.sesame.model.task.videoRewards.VideoRewards;
import com.surexu.sesame.model.task.youthPrivilege.YouthPrivilege;
import com.surexu.sesame.model.task.yueyuezhuan.YueYueZhuan;
import lombok.Getter;

public class ModelOrder {

    @Getter
    private static final List<Class<? extends Model>> clazzList = new ArrayList<>();

    static {
        clazzList.add(BaseModel.class);
        clazzList.add(AntForestV2.class);
        clazzList.add(AncientTree.class);
        clazzList.add(AntFarm.class);
        clazzList.add(AntStall.class);
        clazzList.add(AntOrchard.class);
        clazzList.add(ProtectEcology.class);
        clazzList.add(AntDodo.class);
        clazzList.add(AntAnimalGuess.class);
        clazzList.add(PlantingFlowers.class);
        clazzList.add(AntOcean.class);
        clazzList.add(AntSports.class);
        clazzList.add(SesameDog.class);
        clazzList.add(SesameSports.class);
        clazzList.add(AntMember.class);
        clazzList.add(GreenFinance.class);
        clazzList.add(ConsumeGold.class);
        clazzList.add(goldenbeans.class);
        clazzList.add(OtherTask.class);
        clazzList.add(VideoRewards.class);
        clazzList.add(YouthPrivilege.class);
        clazzList.add(DayDaySave.class);
        clazzList.add(DailyCash.class);
        clazzList.add(LocalTheme.class);
        clazzList.add(LuckCardStatus.class);
        clazzList.add(PromoprodRewards.class);
        clazzList.add(HealthIslandRewards.class);
        clazzList.add(YueYueZhuan.class);
        clazzList.add(AnswerAI.class);

        ExtensionsHandle.handleAlphaRequest("ModelOrder", "addExtensionsClass", clazzList);
    }
}