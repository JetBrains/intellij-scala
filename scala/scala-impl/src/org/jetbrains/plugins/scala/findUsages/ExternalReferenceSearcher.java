package org.jetbrains.plugins.scala.findUsages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.Query;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ExternalReferenceSearcher {
    ExtensionPointName<ExternalReferenceSearcher> EP_NAME =
            ExtensionPointName.create("org.intellij.scala.findUsages.externalReferenceSearcher");

    @NotNull
    Query<PsiReference> search(@NotNull PsiNamedElement target);

    @NotNull
    static Query<PsiReference> searchExternally(@NotNull PsiNamedElement target) {
        final var first = EP_NAME.getExtensions()[0];
        return first.search(target);
    }
}
