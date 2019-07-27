package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jetbrains.idea.devkit.build.AttachIntellijSourcesAction.attachIJSources;

public class IntellijSourcesAttachListener extends ExternalSystemTaskNotificationListenerAdapter {

    private static final Predicate<String> PROJECT_NAME_PATTERN = Pattern.compile("^scalaUltimate|scalaCommunity|((scala-plugin-for-ultimate|intellij-scala)(_\\d+)?)$").asPredicate();
    private static final int MAX_ATTEMPTS = 10;
    private static final int RETRY_DELAY_MS = 1000;

    private static final Logger LOG = Logger.getInstance(IntellijSourcesAttachListener.class);

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
        Project project = id.findProject();
        if (project == null || project.isDisposed())
            return;

        if (PROJECT_NAME_PATTERN.test(project.getName())) {
            tryAttach(project, 0);
        }
    }

    private void tryAttach(Project project, int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            LOG.info("Failed to wait for dumb mode after " + attempt + " attempts, trying to attach sources anyway");
            attachIJSources(project);
        }
        if (DumbService.isDumb(project)) {
            LOG.info("Scheduling sources attach after " + attempt + " attempts");
            DumbService.getInstance(project).runWhenSmart(() -> attachIJSources(project));
        } else {
            // apparently there is no way to run a callback AFTER all external system data has been committed to the project model
            // when the external project is refreshed (counter to imported) "onSuccess" callback is invoked before new libraries model
            // is committed and not in dumb mode so we can't even properly postpone attaching sources
            new Alarm().addRequest(() -> tryAttach(project, attempt+1), RETRY_DELAY_MS);
        }
    }

}
