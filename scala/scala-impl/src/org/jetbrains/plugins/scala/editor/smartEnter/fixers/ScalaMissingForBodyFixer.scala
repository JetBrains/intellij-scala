package org.jetbrains.plugins.scala.editor.smartEnter.fixers

import com.intellij.openapi.editor.{Document, Editor}
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFor

// TODO(SCL-23041): indentation-based syntax support
final class ScalaMissingForBodyFixer extends ScalaForStatementFixerBase {
  override def doApply(forStatement: ScFor)(implicit editor: Editor, document: Document,
                                            processor: ScalaSmartEnterProcessor): OperationPerformed = {
    forStatement.body match {
      case None =>
        val (anchor, text) = forStatement.getRightBracket match {
          case None =>
            val bracketText = forStatement.getLeftBracket.fold("")(matchingBracketText)
            val text = s"$bracketText {}"
            (forStatement, text)
          case Some(bracket) =>
            moveToEnd(editor, bracket)
            (bracket, " {}")
        }
        document.insertString(anchor.endOffset, text)
        WithEnter(text.length - 1)
      case Some(_) => NoOperation
    }
  }
}
