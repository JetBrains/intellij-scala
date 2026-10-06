package org.jetbrains.plugins.scala.lang.refactoring.rename.inplace

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.ScalaBundle

private[inplace] object ScalaMemberInplaceRenamerSupport {
  // Keep the original Throwable handling in Scala while the renamer's inheritance is implemented in Java.
  def performInplaceRename(renamer: ScalaMemberInplaceRenamer, editor: Editor): Boolean = {
    try renamer.performInplaceRefactoring(null)
    catch {
      case c: ControlFlowException => throw c
      case t: Throwable =>
        val element = renamer.getVariable
        val subst = renamer.getSubstituted
        val offset = editor.getCaretModel.getOffset
        val text = editor.getDocument.getImmutableCharSequence
        val before = text.subSequence((offset - 50).max(0), offset).toString
        val after = text.subSequence(offset, (offset + 50).min(text.length))
        val aroundCaret = before + "<caret>" + after
        val message =
          ScalaBundle.message("could.not.perform.inplace.rename", element, element.getName, subst, aroundCaret).stripMargin
        throw new Throwable(message, t)
    }
  }
}
