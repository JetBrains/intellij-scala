package org.jetbrains.plugins.scala.packagesearch.utils;

import com.intellij.pom.Navigatable;
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule;
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion;
import kotlin.jvm.functions.Function3;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

import java.util.Iterator;

public class ScalaKotlinHelper {
    public static <T> Sequence<T> toKotlinSequence(Iterator<T> it) {
        return SequencesKt.asSequence(it);
    }
    public static ProjectModule createNavigatableProjectModule(ProjectModule projectModule, scala.Function3<String, String, PackageVersion, Navigatable> f) {
        return new ProjectModule(
                projectModule.getName(),
                projectModule.getNativeModule(),
                projectModule.getParent(),
                projectModule.getBuildFile(),
                projectModule.getBuildSystemType(),
                projectModule.getModuleType(),
                toKotlinFunction(f)
        );
    }

    public static kotlin.jvm.functions.Function3<String, String, PackageVersion, Navigatable> toKotlinFunction(
            scala.Function3<String, String, PackageVersion, Navigatable> f
    ) {
        return f::apply;
    }

}
