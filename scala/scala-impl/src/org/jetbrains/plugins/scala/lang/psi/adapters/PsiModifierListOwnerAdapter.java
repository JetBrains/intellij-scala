package org.jetbrains.plugins.scala.lang.psi.adapters;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiJvmModifiersOwner;
import org.jetbrains.annotations.NotNull;

//This interface is required because it's impossible to implement
//overloaded method with different array return types in scala.
public interface PsiModifierListOwnerAdapter extends PsiJvmModifiersOwner, PsiAnnotatedAdapter {
    @NotNull
    @Override
    default PsiAnnotation[] getAnnotations() {
        return psiAnnotations();
    }

    @Override
    default boolean hasAnnotation(@NotNull String fqn) {
        return PsiJvmModifiersOwner.super.hasAnnotation(fqn);
    }
}
