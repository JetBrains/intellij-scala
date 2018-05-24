package org.jetbrains.plugins.scala.lang.psi.adapters;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;

/**
 * Nikolay.Tropin
 * 28-Apr-18
 */
public interface PsiTypeParameterAdapter extends PsiTypeParameter, PsiAnnotatedAdapter {
  @NotNull
  @Override
  default PsiAnnotation[] getAnnotations() {
    return psiAnnotations();
  }
}
