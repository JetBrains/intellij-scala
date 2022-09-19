package org.jetbrains.sbt.internal;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.task.ProjectTaskRunner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface InternalDynamicLinker {
    ExtensionPointName<InternalDynamicLinker> EP_NAME =
            ExtensionPointName.create("com.intellij.sbt.internal.dynamicLinker");

    boolean isSbtFile(@NotNull PsiFile file);

    boolean isSbtProjectTaskRunner(@NotNull ProjectTaskRunner runner);

    static boolean checkIsSbtFile(@NotNull PsiFile file) {
        final var first = EP_NAME.getExtensions()[0];
        return first.isSbtFile(file);
    }

    static boolean checkIsSbtProjectTaskRunner(@NotNull ProjectTaskRunner runner) {
        final var first = EP_NAME.getExtensions()[0];
        return first.isSbtProjectTaskRunner(runner);
    }
}
