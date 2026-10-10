package com.surexu.sesame.data.task;

import android.os.Build;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.ThreadUtil;

import java.util.Map;
import java.util.concurrent.*;

public class ProgramChildTaskExecutor implements ChildTaskExecutor {

    private final Map<String, ThreadPoolExecutor> groupChildTaskExecutorMap = new ConcurrentHashMap<>();

    /** 每组允许同时运行的最大子任务线程数（原先上限是 Integer.MAX_VALUE，等于无限起线程） */
    private static final int MAX_CHILD_TASK_THREADS = 16;

    @Override
    public Boolean addChildTask(ModelTask.ChildModelTask childTask) {
        ThreadPoolExecutor threadPoolExecutor = getChildGroupThreadPool(childTask.getGroup());
        Future<?> future;
        long execTime = childTask.getExecTime();
        if (execTime > 0) {
            future = threadPoolExecutor.submit(() -> {
                if (childTask.getIsCancel()) {
                    return;
                }
                //String modelTaskId = getName();
                try {
                    long delay = childTask.getExecTime() - System.currentTimeMillis();
                    if (delay > 0) {
                        try {
                            Thread.sleep(delay);
                        } catch (Exception e) {
                            return;
                        }
                    }
                    childTask.run();
                } catch (Exception e) {
                    Log.printStackTrace(e);
                } finally {
                    childTask.getModelTask().removeChildTask(childTask.getId());
                }
            });
        } else {
            future = threadPoolExecutor.submit(() -> {
                try {
                    childTask.run();
                } catch (Exception e) {
                    Log.printStackTrace(e);
                } finally {
                    childTask.getModelTask().removeChildTask(childTask.getId());
                }
            });
        }
        childTask.setCancelTask(() -> future.cancel(true));
        return true;
    }

    @Override
    public Boolean removeChildTask(ModelTask.ChildModelTask childTask) {
        childTask.cancel();
        return true;
    }

    @Override
    public Boolean clearGroupChildTask(String group) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            groupChildTaskExecutorMap.compute(group, (keyInner, valueInner) -> {
                if (valueInner != null) {
                    ThreadUtil.shutdownAndAwaitTermination(valueInner, 3, TimeUnit.SECONDS);
                }
                return null;
            });
        } else {
            synchronized (groupChildTaskExecutorMap) {
                ThreadPoolExecutor groupThreadPool = groupChildTaskExecutorMap.get(group);
                if (groupThreadPool != null) {
                    ThreadUtil.shutdownAndAwaitTermination(groupThreadPool, 3, TimeUnit.SECONDS);
                    groupChildTaskExecutorMap.remove(group);
                }
            }
        }
        return true;
    }

    @Override
    public Boolean clearAllChildTask() {
        for (ThreadPoolExecutor threadPoolExecutor : groupChildTaskExecutorMap.values()) {
            ThreadUtil.shutdownNow(threadPoolExecutor);
        }
        groupChildTaskExecutorMap.clear();
        return true;
    }

    private ThreadPoolExecutor getChildGroupThreadPool(String group) {
        ThreadPoolExecutor threadPoolExecutor = groupChildTaskExecutorMap.get(group);
        if (threadPoolExecutor != null) {
            return threadPoolExecutor;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            threadPoolExecutor = groupChildTaskExecutorMap.compute(group, (keyInner, valueInner) -> {
                if (valueInner == null) {
                    // 子任务有时效性，必须立即开始，所以保留 SynchronousQueue（不排队）；
                    // 但上限不能是 Integer.MAX_VALUE——那等于"每个子任务新建一条线程"。
                    // 改为有限上限后，超出部分由 CallerRunsPolicy 在提交线程内执行（背压，仍然立即执行不排队）。
                    valueInner = new ThreadPoolExecutor(1, MAX_CHILD_TASK_THREADS, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());
                }
                return valueInner;
            });
        } else {
            synchronized (groupChildTaskExecutorMap) {
                threadPoolExecutor = groupChildTaskExecutorMap.get(group);
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = new ThreadPoolExecutor(1, MAX_CHILD_TASK_THREADS, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());
                    groupChildTaskExecutorMap.put(group, threadPoolExecutor);
                }
            }
        }
        return threadPoolExecutor;
    }

}
