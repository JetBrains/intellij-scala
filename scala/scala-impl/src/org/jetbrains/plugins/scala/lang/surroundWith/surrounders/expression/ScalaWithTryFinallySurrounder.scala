package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScalaWithTryFinallySurrounder extends ScalaWithTrySurrounderBase {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "try {\n" + super.getTemplateAsString(elements) + "\n} finally a"

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription = "try / finally"

  override protected def getRangeToDelete(editor: Editor, tryStmt: ScTry): TextRange =
    tryStmt.finallyBlock match {
      case Some(ScFinallyBlock(expr)) => expr.getTextRange
      case _ => null
    }
}
