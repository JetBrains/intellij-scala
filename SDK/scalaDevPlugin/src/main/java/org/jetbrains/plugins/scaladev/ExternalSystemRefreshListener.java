package org.jetbrains.plugins.scaladev;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ExternalSystemRefreshListener extends ExternalSystemTaskNotificationListenerAdapter {
    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        final Project project = id.findProject();
        if (project != null) {
            final BaseComponent component = project.getComponent(IdeaSourcesAttach.NAME);
            if (component != null && component instanceof IdeaSourcesAttach) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        ((IdeaSourcesAttach) component).attachIdeaSources();
                    }
                });
            }
        }
    }
}
