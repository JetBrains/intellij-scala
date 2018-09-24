package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation}
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

private[clauses] abstract class ClausesCompletionProvider[E <: ScalaPsiElement](clazz: Class[E])
  extends CompletionProvider[CompletionParameters] {

  override final def addCompletions(parameters: CompletionParameters,
                                    context: ProcessingContext,
                                    result: CompletionResultSet): Unit = {
    val position = positionFromParameters(parameters)
    position.parentOfType(clazz).foreach {
      addCompletions(_, position, result)
    }
  }

  protected def addCompletions(typeable: E,
                               position: PsiElement,
                               result: CompletionResultSet): Unit
}

private[clauses] object ClausesCompletionProvider {

  def createLookupElement(lookupString: String)
                         (itemTextItalic: Boolean = false,
                          tailText: String = null)
                         (insertHandler: ClausesInsertHandler[_]): LookupElement =
    LookupElementBuilder.create(lookupString)
      .withInsertHandler(insertHandler)
      .withRenderer {
        (_: LookupElement, presentation: LookupElementPresentation) => {
          presentation.setItemText(lookupString)
          presentation.setItemTextItalic(itemTextItalic)
          presentation.setTailText(tailText, true)
        }
      }
}
