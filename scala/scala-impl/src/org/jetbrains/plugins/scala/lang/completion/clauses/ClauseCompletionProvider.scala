package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

private[clauses] abstract class ClauseCompletionProvider[T <: ScalaPsiElement with Typeable : reflect.ClassTag]
  extends CompletionProvider[CompletionParameters] {

  override final def addCompletions(parameters: CompletionParameters,
                                    context: ProcessingContext,
                                    result: CompletionResultSet): Unit = {
    implicit val place: PsiElement = positionFromParameters(parameters)
    PsiTreeUtil.getParentOfType(place, reflect.classTag.runtimeClass.asInstanceOf[Class[T]]) match {
      case null =>
      case typeable => addCompletions(typeable, result)
    }
  }

  protected def addCompletions(typeable: T, result: CompletionResultSet)
                              (implicit place: PsiElement): Unit
}
