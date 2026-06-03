package org.jetbrains.scalaCli.project;

import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker;
import com.intellij.openapi.project.Project;

public class AutoImportProjectTrackerCompanionProxy {
    public static AutoImportProjectTracker autoImportProjectTracker(Project project) {
        return AutoImportProjectTracker.Companion.getInstance(project);
    }
}
