package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}

private[clauses] abstract class ClauseInsertHandler[E <: ScalaPsiElement : reflect.ClassTag]
  extends InsertHandler[LookupElement] {

  import ClauseInsertHandler._

  protected def handleInsert(implicit context: InsertionContext): Unit

  override final def handleInsert(context: InsertionContext,
                                  lookupElement: LookupElement): Unit =
    handleInsert(context)

  protected final def onTargetElement[U >: Null](onElement: E => U)
                                                (implicit context: InsertionContext): U =
    PsiTreeUtil.getContextOfType(
      context.getFile.findElementAt(context.getStartOffset),
      false,
      reflect.classTag.runtimeClass.asInstanceOf[Class[E]]
    ) match {
      case null => null
      case targetElement => onElement(targetElement)
    }

  protected final def replaceText(text: String)
                                 (implicit context: InsertionContext): Unit = {
    context.getDocument.replaceString(
      context.getStartOffset,
      context.getSelectionEndOffset,
      text
    )
    context.commitDocument()
  }

  protected final def reformatAndMoveCaret(clauses: ScCaseClauses)
                                          (targetClause: ScCaseClause = clauses.caseClause)
                                          (implicit context: InsertionContext): Unit = {
    val InsertionContextExt(editor, doc, file, project) = context

    import collection.JavaConverters._
    CodeStyleManager.getInstance(project).reformatText(
      file,
      patternRanges(clauses).asJava
    )

    val newLine = findNewLine(clauses, targetClause)
    val spaceAndNewLine = newLine.replaceWithText(" " + newLine.getText)

    PsiDocumentManager.getInstance(project)
      .doPostponedOperationsAndUnblockDocument(doc)
    editor.getCaretModel
      .moveToOffset(1 + spaceAndNewLine.getStartOffset)
  }
}

object ClauseInsertHandler {

  private def patternRanges(clauses: ScCaseClauses) = for {
    clause <- clauses.caseClauses
    Some(arrow) = clause.funType
  } yield TextRange.from(clause.getTextOffset, arrow.getStartOffsetInParent)

  private def findNewLine(clauses: ScCaseClauses, clause: ScCaseClause) =
    (clause.getNextSibling match {
      case null => clauses.getNextSibling
      case sibling => sibling
    }).asInstanceOf[PsiWhiteSpaceImpl]
}
