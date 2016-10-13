package org.jetbrains.plugins.scaladev;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public class ExternalSystemRefreshListener extends ExternalSystemTaskNotificationListenerAdapter {

    final private Logger LOG = Logger.getInstance(this.getClass());

    final static private int MAX_RETRIES = 3;
    final static private int DELAY = 5000;

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        final Project project = id.findProject();
        if (project != null) {
            final BaseComponent component = project.getComponent(IdeaSourcesAttach.NAME);
            if (component != null && component instanceof IdeaSourcesAttach) {
                final IdeaSourcesAttach attach = (IdeaSourcesAttach) component;
                    ApplicationManager.getApplication().invokeLater(getRunnable(attach, 0));
            }
        }
    }

    @NotNull
    private Runnable getRunnable(final IdeaSourcesAttach attach, final int tryNum) {
        return new Runnable() {
            @Override
            public void run() {
                if (!attach.getLibsWithoutSourceRoots().isEmpty()) {
                    attach.attachIdeaSources();
                } else if (tryNum < MAX_RETRIES){
                    // onSuccess is sometimes called before unmanagedJars has finished importing
                    LOG.info("No candidates found, rescheduling check for " + DELAY);
                    new Alarm().addRequest(getRunnable(attach, tryNum + 1), DELAY);
                }
            }
        };
    }

}
