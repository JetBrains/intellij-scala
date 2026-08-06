package org.jetbrains.plugins.scala.findUsages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ExternalSearchScopeChecker {
    ExtensionPointName<ExternalSearchScopeChecker> EP_NAME =
            ExtensionPointName.create("org.intellij.scala.findUsages.externalSearchScopeChecker");

    boolean checkSearchScopeIsSufficient(@NotNull PsiNamedElement target, @NotNull UsageType usageType);

    static boolean checkSearchScopeIsSufficientExternally(@NotNull PsiNamedElement target, @NotNull UsageType usageType) {
        final var first = EP_NAME.getExtensions()[0];
        return first.checkSearchScopeIsSufficient(target, usageType);
    }
}
