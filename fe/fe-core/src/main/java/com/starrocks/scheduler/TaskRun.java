// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.
package com.starrocks.scheduler;

import com.google.common.collect.Maps;
import com.starrocks.analysis.StringLiteral;
import com.starrocks.analysis.UserIdentity;
import com.starrocks.common.Config;
import com.starrocks.common.DdlException;
import com.starrocks.load.loadv2.InsertLoadJob;
import com.starrocks.qe.ConnectContext;
import com.starrocks.qe.QueryState;
import com.starrocks.qe.StmtExecutor;
import com.starrocks.scheduler.persist.TaskRunStatus;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.sql.ast.SetVar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;

public class TaskRun implements Comparable<TaskRun> {

    private static final Logger LOG = LogManager.getLogger(TaskRun.class);

    private long taskId;

    private Map<String, String> properties;

    private Future<?> future;

    private Task task;

    private ConnectContext runCtx;

    private TaskRunProcessor processor;

    private TaskRunStatus status;

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public Future<?> getFuture() {
        return future;
    }

    public void setFuture(Future<?> future) {
        this.future = future;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public TaskRunProcessor getProcessor() {
        return processor;
    }

    public void setProcessor(TaskRunProcessor processor) {
        this.processor = processor;
    }

    public boolean executeTaskRun() throws Exception {
        TaskRunContext taskRunContext = new TaskRunContext();
        taskRunContext.setDefinition(status.getDefinition());
        runCtx = new ConnectContext(null);
        runCtx.setGlobalStateMgr(GlobalStateMgr.getCurrentState());
        runCtx.setDatabase(task.getDbName());
        runCtx.setQualifiedUser(status.getUser());
        runCtx.setCurrentUserIdentity(UserIdentity.createAnalyzedUserIdentWithIp(status.getUser(), "%"));
        runCtx.getState().reset();
        runCtx.setQueryId(UUID.fromString(status.getQueryId()));
        Map<String, String> taskRunContextProperties = Maps.newHashMap();
        runCtx.resetSessionVariable();
        if (properties != null) {
            for (String key : properties.keySet()) {
                try {
                    runCtx.modifySessionVariable(new SetVar(key, new StringLiteral(properties.get(key))), true);
                } catch (DdlException e) {
                    // not session variable
                    taskRunContextProperties.put(key, properties.get(key));
                }
            }
        }
        taskRunContext.setCtx(runCtx);
        taskRunContext.setRemoteIp(runCtx.getMysqlChannel().getRemoteHostPortString());
        taskRunContext.setProperties(taskRunContextProperties);
        processor.processTaskRun(taskRunContext);
        QueryState queryState = runCtx.getState();
        if (runCtx.getState().getStateType() == QueryState.MysqlStateType.ERR) {
            status.setErrorMessage(queryState.getErrorMessage());
            int errorCode = -1;
            if (queryState.getErrorCode() != null) {
                errorCode = queryState.getErrorCode().getCode();
            }
            status.setErrorCode(errorCode);
            return false;
        }
        return true;
    }

    public ConnectContext getRunCtx() {
        return runCtx;
    }

    public TaskRunStatus getStatus() {
        if (status == null) {
            return null;
        }
        switch (status.getState()) {
            case RUNNING:
                if (runCtx != null) {
                    StmtExecutor executor = runCtx.getExecutor();
                    if (executor != null && executor.getCoordinator() != null) {
                        long jobId = executor.getCoordinator().getJobId();
                        if (jobId != -1) {
                            InsertLoadJob job = (InsertLoadJob) GlobalStateMgr.getCurrentState()
                                    .getLoadManager().getLoadJob(jobId);
                            int progress = job.getProgress();
                            if (progress == 100) {
                                progress = 99;
                            }
                            status.setProgress(progress);
                        }
                    }
                }
                break;
            case SUCCESS:
                status.setProgress(100);
                break;
        }
        return status;
    }

    public TaskRunStatus initStatus(String queryId, Long createTime) {
        TaskRunStatus status = new TaskRunStatus();
        status.setQueryId(queryId);
        status.setTaskName(task.getName());
        if (createTime == null) {
            status.setCreateTime(System.currentTimeMillis());
        } else {
            status.setCreateTime(createTime);
        }
        status.setUser(task.getCreateUser());
        status.setDbName(task.getDbName());
        status.setDefinition(task.getDefinition());
        status.setExpireTime(System.currentTimeMillis() + Config.task_runs_ttl_second * 1000L);
        this.status = status;
        return status;
    }

    @Override
    public int compareTo(@NotNull TaskRun taskRun) {
        if (this.getStatus().getPriority() != taskRun.getStatus().getPriority()) {
            return taskRun.getStatus().getPriority() - this.getStatus().getPriority();
        } else {
            return this.getStatus().getCreateTime() > taskRun.getStatus().getCreateTime() ? 1 : -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskRun taskRun = (TaskRun) o;
        return status.getDefinition().equals(taskRun.getStatus().getDefinition());
    }

    @Override
    public int hashCode() {
        return Objects.hash(status);
    }
}
