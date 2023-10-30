package org.jetbrains.idea.devkit.scala;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.idea.devkit.scala.AttachIntellijSourcesAction.attachIJSources;
import static org.jetbrains.idea.devkit.scala.SbtIdeaPluginDetector.hasSbtIdeaPlugin;

public class IntellijSourcesAttachListener extends ExternalSystemTaskNotificationListenerAdapter {

    private static final int MAX_ATTEMPTS = 10;
    private static final int RETRY_DELAY_MS = 3000;

    private static final Logger LOG = Logger.getInstance(IntellijSourcesAttachListener.class);

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        Project project = id.findProject();
        if (project == null || project.isDisposed())
            return;
        if (!ApplicationManager.getApplication().isInternal())
            return;

        if (hasSbtIdeaPlugin(project)) {
            tryAttach(project, 0);
        }
    }

    private void tryAttach(Project project, int attempt) {
        if (project.isDisposed()) return;
        if (attempt >= MAX_ATTEMPTS) {
            LOG.info("Failed to wait for dumb mode after " + attempt + " attempts, trying to attach sources anyway");
            attachIJSources(project);
            return;
        }
        if (DumbService.isDumb(project)) {
            LOG.info("Scheduling sources attach after " + attempt + " attempts");
            DumbService.getInstance(project).runWhenSmart(() -> attachIJSources(project));
        } else {
            // apparently there is no way to run a callback AFTER all external system data has been committed to the project model
            // when the external project is refreshed (counter to imported) "onSuccess" callback is invoked before new libraries model
            // is committed and not in dumb mode so we can't even properly postpone attaching sources
            //NOTE: `addRequest` schedules a single request, it doesn't call it periodically
            new Alarm().addRequest(() -> tryAttach(project, attempt + 1), RETRY_DELAY_MS);
        }
    }

}
