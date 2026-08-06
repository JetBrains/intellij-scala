package org.jetbrains.plugins.scala.lang.psi.adapters;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

//This interface is required because it's impossible to implement
//overloaded method with different array return types in scala.
public interface PsiParameterAdapter extends PsiParameter, PsiModifierListOwnerAdapter {
    @NotNull
    @Override
    default PsiAnnotation[] getAnnotations() {
        return psiAnnotations();
    }
}
