package org.jetbrains.plugins.scala.lang.refactoring.rename.inplace;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;

/**
 * Nikolay.Tropin
 * 6/21/13
 */
class DialogRenamer extends VariableInplaceRenameHandler {
  public void doDialogRename(PsiElement element, Project project, PsiElement nameSuggestionContext, Editor editor) {
    PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor);
  }
}
