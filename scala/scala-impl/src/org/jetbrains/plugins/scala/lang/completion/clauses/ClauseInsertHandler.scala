package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import java.{util => ju}

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses

private[clauses] abstract class ClauseInsertHandler[E <: ScalaPsiElement : reflect.ClassTag]
  extends InsertHandler[LookupElement] {

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

  protected final def reformatClauses(clauses: ScCaseClauses)
                                     (implicit context: InsertionContext): Unit =
    CodeStyleManager.getInstance(context.getProject).reformatText(
      context.getFile,
      ju.Collections.singleton(clauses.getTextRange)
    )

  protected final def moveCaret(offset: Int)
                               (implicit context: InsertionContext): Unit = {
    val InsertionContextExt(editor, document, _, project) = context
    PsiDocumentManager.getInstance(project)
      .doPostponedOperationsAndUnblockDocument(document)
    editor.getCaretModel.moveToOffset(offset)
  }
}
