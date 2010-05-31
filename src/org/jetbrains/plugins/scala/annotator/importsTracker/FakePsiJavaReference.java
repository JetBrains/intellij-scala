package org.jetbrains.plugins.scala.annotator.importsTracker;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */
class FakePsiJavaReference implements PsiJavaReference {
  public void processVariants(PsiScopeProcessor processor) {
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    return null;
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    return new JavaResolveResult[0];
  }

  public PsiElement getElement() {
    return null;
  }

  public TextRange getRangeInElement() {
    return null;
  }

  public PsiElement resolve() {
    return null;
  }

  public String getCanonicalText() {
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return false;
  }

  @NotNull
  public Object[] getVariants() {
    return new Object[0];
  }

  public boolean isSoft() {
    return false;
  }
}
