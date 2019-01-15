package org.jetbrains.plugins.scala

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
  * @author adkozlov
  */
trait ScalaCodeInsightActionHandler extends LanguageCodeInsightActionHandler {
  override def isValidFor(editor: Editor, file: PsiFile): Boolean =
    (editor, file) match {
      case (null, null) => false
      case _ => file.getFileType == ScalaFileType.INSTANCE
    }
}
