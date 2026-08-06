package org.jetbrains.sbt.project;

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
import java.util.function.BiConsumer;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.junit.Assert.fail;

// TODO: this is a workaround to restore previous importing behaviour (modified in IDEA-388789)
//       delete this class when a better solution is ready
@SuppressWarnings({"deprecation", "rawtypes", "unchecked", "UnstableApiUsage"})
public class ExternalSystemImportingTestCaseProxy {
    // copy-pasted from ExternalSystemImportingTestCase
    // commented-out `waitForProjectActivity` call to avoid hanging tests
    // restored project indexing at the end of the external system import
    public static void importProject(Project project,
                              ProjectSystemId externalSystemId,
                              ExternalProjectSettings projectSettings,
                              String projectPath,
                              ImportSpec importSpec,
                              BiConsumer<String, String> handleImportFailure) {
        AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(project, externalSystemId);
        projectSettings.setExternalProjectPath(projectPath);
        //noinspection unchecked
        Set<ExternalProjectSettings> projects = new HashSet<>(systemSettings.getLinkedProjectsSettings());
        projects.remove(projectSettings);
        projects.add(projectSettings);
        //noinspection unchecked
        systemSettings.setLinkedProjectsSettings(projects);

        final Ref<Couple<String>> error = Ref.create();
        ExternalProjectRefreshCallback callback = importSpec.getCallback();
        ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(importSpec);
        if (callback == null || callback instanceof ImportSpecBuilder.DefaultProjectRefreshCallback) {
            importSpecBuilder.callback(new ExternalProjectRefreshCallback() {
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

        // await for all background activities to complete
//        waitForProjectActivity(getMyProject(), () ->
        ExternalSystemUtil.refreshProjects(importSpecBuilder);
//        );

        if (!error.isNull()) {
            handleImportFailure.accept(error.get().first, error.get().second);
        }

        // allow all the invokeLater to pass through the queue, before waiting for indexes to be ready
        // (specifically, all the invokeLater that schedule indexing after language level change performed by import)
        runInEdtAndWait(PlatformTestUtil::dispatchAllEventsInIdeEventQueue);
        IndexingTestUtil.waitUntilIndexesAreReady(project);
    }

    public static void handleImportFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        String failureMsg = "Import failed: " + errorMessage;
        if (StringUtil.isNotEmpty(errorDetails)) {
            failureMsg += "\nError details: \n" + errorDetails;
        }
        fail(failureMsg);
    }

    public static ImportSpec createImportSpec(Project project, ProjectSystemId systemId) {
        ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(project, systemId)
                .use(ProgressExecutionMode.MODAL_SYNC);
        return importSpecBuilder.build();
    }
}
