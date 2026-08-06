package org.jetbrains.plugins.scala.editor.smartEnter.fixers

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiErrorElement, SyntaxTraverser}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.editor.smartEnter.fixers.ScalaForStatementFixerBase.mapping
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFor}

abstract class ScalaForStatementFixerBase extends ScalaFixer {
  final override def apply(editor: Editor, processor: ScalaSmartEnterProcessor,
                           psiElement: PsiElement): OperationPerformed = {
    val forStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScFor], false)
    if (forStatement == null) NoOperation
    else doApply(forStatement)(editor, editor.getDocument, processor)
  }

  protected def doApply(forStatement: ScFor)(implicit editor: Editor, document: Document,
                                             processor: ScalaSmartEnterProcessor): OperationPerformed

  protected final def matchingBracketText(bracket: PsiElement): String = mapping(bracket.getText)

  protected final def placeInWholeBodyBlock(forStatement: ScFor, editor: Editor): OperationPerformed =
    forStatement.body match {
      case Some(block: ScBlockExpr) => placeInWholeBlock(block, editor)
      case _ => NoOperation
    }

  protected final def hasRelevantMissingRightBraceErrorAfter(forStatement: ScFor, anchor: PsiElement): Boolean = {
    if (forStatement.getLeftBracket.forall(_.textMatches("("))) false
    else {
      val offset = anchor.endOffset
      val psiApi = SyntaxTraverser.psiApi()
      forStatement
        .parentsInFile
        .exists(parent => isRightBraceExpectedErrorAfter(psiApi.last(parent), offset))
    }
  }

  private def isRightBraceExpectedErrorAfter(@Nullable element: PsiElement, offset: Int): Boolean = element match {
    case error: PsiErrorElement if error.startOffset > offset => error.getErrorDescription == ScalaBundle.message("rbrace.expected")
    case _ => false
  }
}

object ScalaForStatementFixerBase {
  private val mapping = Map(
    "{" -> "}",
    "}" -> "{",
    "(" -> ")",
    ")" -> "(",
  ).withDefaultValue("")
}
