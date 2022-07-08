package org.jetbrains.plugins.scala.lang.psi.adapters;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import org.jetbrains.annotations.NotNull;

//This interface is required because it's impossible to implement
//overloaded method with different array return types in scala.
public interface PsiAnnotatedAdapter extends PsiAnnotationOwner {
    PsiAnnotation[] psiAnnotations();

    @NotNull
    @Override
    default PsiAnnotation[] getAnnotations() {
        return psiAnnotations();
    }
}
