package org.jetbrains.idea.devkit.scala;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.sbt.project.module.SbtModuleType$;

import java.util.Arrays;
import java.util.Objects;

public class SbtIdeaPluginDetector {

    private static final String SBT_IDEA_PLUGIN = "sbt-idea-plugin";

    public static boolean hasSbtIdeaPlugin(Project project) {
        return Arrays.stream(ModuleManager.getInstance(project).getModules())
                .filter(module -> Objects.equals(module.getModuleTypeName(), SbtModuleType$.MODULE$.Id()))
                .map(module -> OrderEntryUtil.getModuleLibraries(ModuleRootManager.getInstance(module)))
                .anyMatch(libraries -> libraries.stream().anyMatch(SbtIdeaPluginDetector::isSbtIdeaPluginJar));
    }

    private static boolean isSbtIdeaPluginJar(Library library) {
        return Arrays.stream(library.getFiles(OrderRootType.CLASSES))
                .anyMatch(virtualFile -> virtualFile.getName().contains(SBT_IDEA_PLUGIN));
    }
}
