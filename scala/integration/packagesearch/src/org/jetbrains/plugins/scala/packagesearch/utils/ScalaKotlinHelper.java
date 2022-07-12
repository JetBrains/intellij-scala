package org.jetbrains.plugins.scala.packagesearch.utils;

import com.intellij.pom.Navigatable;
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule;
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion;

import static java.util.Collections.emptyList;

@SuppressWarnings("UnstableApiUsage")
public class ScalaKotlinHelper {

    public static ProjectModule createNavigatableProjectModule(ProjectModule projectModule, scala.Function3<String, String, PackageVersion, Navigatable> navigatableDependency) {
        return new ProjectModule(
                projectModule.getName(),
                projectModule.getNativeModule(),
                projectModule.getParent(),
                projectModule.getBuildFile(),
                projectModule.getProjectDir(),
                projectModule.getBuildSystemType(),
                projectModule.getModuleType(),
                emptyList()
                //TODO: use navigatableDependency, see SCL-20365
        );
    }
}
