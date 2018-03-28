package org.jetbrains.plugins.scaladev;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public class ExternalSystemRefreshListener extends ExternalSystemTaskNotificationListenerAdapter {

    private final static Logger LOG = Logger.getInstance(ExternalSystemRefreshListener.class);

    private final static int MAX_RETRIES = 3;
    private final static int DELAY = 5000;

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        Project project = id.findProject();
        if (project == null) return;

        IdeaSourcesAttach attach = project.getComponent(IdeaSourcesAttach.class);
        if (attach == null) return;

        ApplicationManager.getApplication().invokeLater(createTask(attach, 0));
    }

    @NotNull
    private static Runnable createTask(@NotNull IdeaSourcesAttach attach, int tryNum) {
        return () -> {
            if (!attach.needsAttaching().isEmpty()) {
                attach.attachIdeaSources();
            } else if (tryNum < MAX_RETRIES) {
                // onSuccess is sometimes called before unmanagedJars has finished importing
                LOG.info("No candidates found, rescheduling check for " + DELAY);
                new Alarm().addRequest(createTask(attach, tryNum + 1), DELAY);
            }
        };
    }

}
