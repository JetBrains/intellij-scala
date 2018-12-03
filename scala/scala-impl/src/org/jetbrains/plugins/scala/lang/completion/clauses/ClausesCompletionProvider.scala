package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

private[clauses] abstract class ClausesCompletionProvider[T <: ScalaPsiElement with Typeable](clazz: Class[T])
  extends CompletionProvider[CompletionParameters] {

  override final def addCompletions(parameters: CompletionParameters,
                                    context: ProcessingContext,
                                    result: CompletionResultSet): Unit = {
    val position = positionFromParameters(parameters)
    PsiTreeUtil.getParentOfType(position, clazz) match {
      case null =>
      case typeable => addCompletions(typeable, position, result)
    }
  }

  protected def addCompletions(typeable: T,
                               position: PsiElement,
                               result: CompletionResultSet): Unit
}

private[clauses] object ClausesCompletionProvider {

  implicit class CompletionResultSetExt(private val result: CompletionResultSet) extends AnyVal {

    def addElement(lookupString: String,
                   insertHandler: ClausesInsertHandler[_])
                  (itemTextItalic: Boolean = false,
                   tailText: String = null): Unit = {
      val lookupElement = LookupElementBuilder.create(lookupString)
        .withInsertHandler(insertHandler)
        .withRenderer {
          (_: LookupElement, presentation: LookupElementPresentation) => {
            presentation.setItemText(lookupString)
            presentation.setItemTextItalic(itemTextItalic)
            presentation.setTailText(tailText, true)
          }
        }

      result.addElement(lookupElement)
    }
  }

}
