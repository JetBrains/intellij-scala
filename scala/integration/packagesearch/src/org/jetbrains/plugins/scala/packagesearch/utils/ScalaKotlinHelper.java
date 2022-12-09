package org.jetbrains.plugins.scala.packagesearch.utils;

import com.intellij.buildsystem.model.DeclaredDependency;
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes;
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule;

import java.util.concurrent.CompletableFuture;

import static java.util.Collections.emptyList;

@SuppressWarnings("UnstableApiUsage")
public class ScalaKotlinHelper {

    public static PackageSearchModule createNavigatableProjectModule(
            PackageSearchModule projectModule,
            scala.Function1<DeclaredDependency, CompletableFuture<DependencyDeclarationIndexes>> dependencyDeclarationCallback
    ) {
        return new PackageSearchModule(
                projectModule.getName(),
                projectModule.getNativeModule(),
                projectModule.getParent(),
                projectModule.getBuildFile(),
                projectModule.getProjectDir(),
                projectModule.getBuildSystemType(),
                projectModule.getModuleType(),
                emptyList(),
                dependencyDeclarationCallback::apply
        );
    }
}
