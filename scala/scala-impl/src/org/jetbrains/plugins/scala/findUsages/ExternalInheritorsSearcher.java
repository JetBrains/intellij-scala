package org.jetbrains.plugins.scala.findUsages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ExternalInheritorsSearcher {
    ExtensionPointName<ExternalInheritorsSearcher> EP_NAME =
            ExtensionPointName.create("org.intellij.scala.findUsages.externalInheritorsSearcher");

    @NotNull
    Query<PsiElement> search(@NotNull PsiClass cls, @NotNull SearchScope scope, boolean checkDeep);

    @NotNull
    static Query<PsiElement> searchExternally(@NotNull PsiClass cls, @NotNull SearchScope scope, boolean checkDeep) {
        final var first = EP_NAME.getExtensions()[0];
        return first.search(cls, scope, checkDeep);
    }
}
