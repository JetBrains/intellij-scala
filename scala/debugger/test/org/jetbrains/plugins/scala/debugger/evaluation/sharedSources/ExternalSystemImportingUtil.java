package org.jetbrains.plugins.scala.debugger.evaluation.sharedSources;

import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static junit.framework.TestCase.fail;

/**
 * Copy-pasted with minor modifications from
 * {@link com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase}.
 */
class ExternalSystemImportingUtil {
    private ExternalSystemImportingUtil() {}

    static void importProject(
            Project project,
            ProjectSystemId systemId,
            ExternalProjectSettings projectSettings,
            String projectPath,
            @Nullable Boolean skipIndexing
    ) {
        if (skipIndexing != null) {
            PlatformTestUtil.withSystemProperty("idea.skip.indices.initialization", skipIndexing.toString(),
                    () -> importProject(project, systemId, projectSettings, projectPath));
        }
        else {
            importProject(project, systemId, projectSettings, projectPath);
        }
    }

    private static void importProject(Project project, ProjectSystemId systemId, ExternalProjectSettings projectSettings, String projectPath) {
        AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(project, systemId);
        projectSettings.setExternalProjectPath(projectPath);
        //noinspection unchecked
        Set<ExternalProjectSettings> projects = new HashSet<>(systemSettings.getLinkedProjectsSettings());
        projects.remove(projectSettings);
        projects.add(projectSettings);
        //noinspection unchecked
        systemSettings.setLinkedProjectsSettings(projects);

        final Ref<Couple<String>> error = Ref.create();
        ImportSpec importSpec = createImportSpec(project, systemId);
        ExternalProjectRefreshCallback callback = importSpec.getCallback();
        if (callback == null || callback instanceof ImportSpecBuilder.DefaultProjectRefreshCallback) {
            importSpec = new ImportSpecBuilder(importSpec).callback(new ExternalProjectRefreshCallback() {
                @Override
                public void onSuccess(final @Nullable DataNode<ProjectData> externalProject) {
                    if (externalProject == null) {
                        System.err.println("Got null External project after import");
                        return;
                    }
                    try {
                        ProjectDataManager.getInstance().importData(externalProject, project);
                    } catch (Throwable ex) {
                        ex.printStackTrace(System.err);
                        error.set(Couple.of("Exception occurred in `ProjectDataManager.importData` (see output for the details)", null));
                    }
                }

                @Override
                public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
                    error.set(Couple.of(errorMessage, errorDetails));
                }
            }).build();
        }

        ExternalSystemUtil.refreshProjects(importSpec);

        if (!error.isNull()) {
            handleImportFailure(error.get().first, error.get().second);
        }

        // allow all the invokeLater to pass through the queue, before waiting for indexes to be ready
        // (specifically, all the invokeLater that schedule indexing after language level change performed by import)
        runInEdtAndWait(() -> PlatformTestUtil.dispatchAllEventsInIdeEventQueue());
        IndexingTestUtil.waitUntilIndexesAreReady(project);
    }

    private static void handleImportFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        String failureMsg = "Import failed: " + errorMessage;
        if (StringUtil.isNotEmpty(errorDetails)) {
            failureMsg += "\nError details: \n" + errorDetails;
        }
        fail(failureMsg);
    }

    private static ImportSpec createImportSpec(Project project, ProjectSystemId systemId) {
        ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(project, systemId)
                .use(ProgressExecutionMode.MODAL_SYNC);
        return importSpecBuilder.build();
    }
}
