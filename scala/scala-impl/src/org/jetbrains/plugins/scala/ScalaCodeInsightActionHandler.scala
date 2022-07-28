package org.jetbrains.plugins.scala

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

trait ScalaCodeInsightActionHandler extends LanguageCodeInsightActionHandler {

  override def isValidFor(editor: Editor, file: PsiFile): Boolean =
    editor != null &&
      file != null &&
      file.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
}
