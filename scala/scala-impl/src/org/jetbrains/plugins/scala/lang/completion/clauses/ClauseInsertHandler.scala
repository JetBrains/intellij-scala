package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil.{getContextOfType, getNextSiblingOfType}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}

private[clauses] abstract class ClauseInsertHandler[
  E <: ScalaPsiElement : reflect.ClassTag
] extends InsertHandler[LookupElement] {

  protected implicit def projectFromContext(implicit context: InsertionContext): Project = context.getProject

  protected def handleInsert(implicit context: InsertionContext): Unit

  override final def handleInsert(context: InsertionContext,
                                  lookupElement: LookupElement): Unit =
    handleInsert(context)

  protected final def onTargetElement[U >: Null](onElement: E => U)
                                                (implicit context: InsertionContext): U =
    getContextOfType(
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

  protected final def reformatAndMoveCaret(clauses: ScCaseClauses, targetClause: ScCaseClause,
                                           rangesToReformat: TextRange*)
                                          (implicit context: InsertionContext): Unit = {
    val InsertionContextExt(editor, doc, file, project) = context

    import scala.jdk.CollectionConverters._
    CodeStyleManager.getInstance(project)
      .reformatText(file, rangesToReformat.asJava)

    val nextLeaf = findNextLeaf(targetClause) match {
      case null => findNextLeaf(clauses)
      case sibling: LeafPsiElement => sibling
    }

    val spaceAndNextLeaf = nextLeaf
      .replaceWithText(" " + nextLeaf.getText)

    PsiDocumentManager.getInstance(project)
      .doPostponedOperationsAndUnblockDocument(doc)
    editor.getCaretModel
      .moveToOffset(1 + spaceAndNextLeaf.getStartOffset)
  }

  private def findNextLeaf(sibling: ScalaPsiElement) =
    getNextSiblingOfType(sibling, classOf[LeafPsiElement])
}

