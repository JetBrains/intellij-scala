package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet}
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

private[clauses] abstract class ClauseCompletionProvider[
  T <: ScalaPsiElement with Typeable : reflect.ClassTag
] extends CompletionProvider[CompletionParameters] {

  override final def addCompletions(parameters: CompletionParameters,
                                    context: ProcessingContext,
                                    result: CompletionResultSet): Unit = {
    val place = positionFromParameters(parameters)
    getParentOfType(place, reflect.classTag.runtimeClass.asInstanceOf[Class[T]]) match {
      case null =>
      case typeable => addCompletions(typeable, result)(ClauseCompletionParameters(place, parameters.getOriginalFile.getResolveScope))
    }
  }

  protected def addCompletions(typeable: T, result: CompletionResultSet)
                              (implicit parameters: ClauseCompletionParameters): Unit
}
