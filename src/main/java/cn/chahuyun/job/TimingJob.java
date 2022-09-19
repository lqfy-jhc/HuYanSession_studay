package cn.chahuyun.job;

import cn.chahuyun.HuYanSession;
import cn.chahuyun.config.ConfigData;
import cn.chahuyun.controller.QuartzAction;
import cn.chahuyun.data.StaticData;
import cn.chahuyun.entity.*;
import cn.chahuyun.utils.DynamicMessageUtil;
import cn.hutool.cron.pattern.CronPattern;
import cn.hutool.cron.task.CronTask;
import cn.hutool.cron.task.Task;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.ContactList;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.message.code.MiraiCode;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.utils.MiraiLogger;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 定时器的job
 *
 * @author Moyuyanli
 * @Date 2022/8/27 19:20
 */
public class TimingJob implements Task {

    private final String id;


    public TimingJob(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * 获取cron任务类
     *
     * @param id   id
     * @param cron cron表达式
     * @return cn.hutool.cron.task.CronTask
     * @author Moyuyanli
     * @date 2022/9/19 17:46
     */
    public static CronTask createTask(String id, String cron) {
        TimingJob timingJob = new TimingJob(id);
        return new CronTask(id, CronPattern.of(cron), timingJob);
    }

    @Override
    public void execute() {
        MiraiLogger log = HuYanSession.log;
        int id = Integer.parseInt(getId().split("\\.")[0]);
        QuartzInfo base = QuartzAction.getQuartzInfo(id);
        Map<Integer, GroupList> groupList = StaticData.getGroupListMap(base.getBot());

        Scope scope = base.getScope();
        if (ConfigData.INSTANCE.getDebugSwitch()) {
            log.info("定时器" + base.getId() + "-" + base.getName() + "执行!");
        }
        long botQq = base.getBot();
        Bot bot = Bot.getInstance(botQq);
        if (scope.isGlobal()) {
            ContactList<Group> groups = bot.getGroups();
            for (Group group : groups) {
                dialogue(base, group);
            }
        } else if (scope.isGroupInfo()) {
            GroupList groupListInfo = groupList.get(scope.getListId());
            List<GroupInfo> groups = groupListInfo.getGroups();
            for (GroupInfo groupInfo : groups) {
                Group group = bot.getGroup(groupInfo.getGroupId());
                if (group == null) {
                    log.warning(String.format("群 %d 不存在！", groupInfo.getGroupId()));
                    continue;
                }
                dialogue(base, group);
            }
        }
        Group group = bot.getGroup(scope.getGroupNumber());
        dialogue(base, group);
    }

    /**
     * 定时任务的消息发送
     *
     * @param quartzInfo 定时器的实体
     * @author Moyuyanli
     * @date 2022/8/27 23:16
     */
    private void dialogue(QuartzInfo quartzInfo, Group group) {
        boolean random = quartzInfo.isRandom();
        boolean polling = quartzInfo.isPolling();
        List<ManySession> manySessions = quartzInfo.getManySessions();

        if (!random && !polling) {
            sendMessage(group, quartzInfo.isDynamic(), quartzInfo.isOther(), quartzInfo.getReply());
        } else {
            int size = manySessions.size();
            if (random) {
                ManySession manySession = manySessions.get((int) (Math.random() * size - 1));
                sendMessage(group, manySession.isDynamic(), manySession.isOther(), manySession.getReply());
            } else {
                int pollingNumber = quartzInfo.getPollingNumber();
                ManySession manySession = manySessions.get(pollingNumber < size ? pollingNumber : pollingNumber % size);
                sendMessage(group, manySession.isDynamic(), manySession.isOther(), manySession.getReply());
                QuartzAction.increase(quartzInfo);
            }
        }
    }

    /**
     * 判断发送消息类型
     *
     * @param group   群
     * @param dynamic 是否包含动态消息
     * @param other   是否是其他类型消息
     * @param reply   消息内容
     * @author Moyuyanli
     * @date 2022/8/27 23:41
     */
    private void sendMessage(Group group, boolean dynamic, boolean other, String reply) {
        if (dynamic) {
            group.sendMessage(Objects.requireNonNull(DynamicMessageUtil.parseMessageParameter(reply, group)));
        } else if (other) {
            group.sendMessage(MessageChain.deserializeFromJsonString(reply));
        } else {
            group.sendMessage(MiraiCode.deserializeMiraiCode(reply));
        }
    }


}
