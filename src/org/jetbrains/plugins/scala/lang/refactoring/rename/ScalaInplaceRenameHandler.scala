package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.openapi.actionSystem.{DataContext, LangDataKeys, PlatformDataKeys}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.rename.PsiElementRenameHandler

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

class ScalaInplaceRenameHandler extends VariableInplaceRenameHandler{
  override def isAvailableOnDataContext(dataContext: DataContext): Boolean = {
    val element = PsiElementRenameHandler.getElement(dataContext)
    val editor = PlatformDataKeys.EDITOR.getData(dataContext)
    val file = LangDataKeys.PSI_FILE.getData(dataContext)
    if (editor == null || file == null) return false
    val nameSuggestionContext = file.findElementAt(editor.getCaretModel.getOffset)

    val supportProvider: RefactoringSupportProvider = if (element != null)
      LanguageRefactoringSupport.INSTANCE.forLanguage(element.getLanguage)
    else null
    return supportProvider != null &&
           editor.getSettings.isVariableInplaceRenameEnabled &&
           supportProvider.doInplaceRenameFor(element, nameSuggestionContext);
  }
}