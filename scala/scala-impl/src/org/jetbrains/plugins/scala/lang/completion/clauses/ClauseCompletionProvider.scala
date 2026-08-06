package org.jetbrains.plugins.scala.lang.completion.clauses

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet}
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.positionFromParameters
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

import scala.reflect.{ClassTag, classTag}

private[clauses] abstract class ClauseCompletionProvider[
  T <: ScalaPsiElement with Typeable : ClassTag
] extends CompletionProvider[CompletionParameters] {

  override final def addCompletions(parameters: CompletionParameters,
                                    context: ProcessingContext,
                                    result: CompletionResultSet): Unit = {
    val place = positionFromParameters(parameters)
    getContextOfType(place, classTag.runtimeClass.asInstanceOf[Class[T]]) match {
      case null =>
      case typeable =>
        val originalFile = parameters.getOriginalFile
        val clauseParameters = ClauseCompletionParameters(place, originalFile.getResolveScope, parameters.getInvocationCount)
        addCompletions(typeable, result)(clauseParameters)
    }
  }

  protected def addCompletions(typeable: T, result: CompletionResultSet)
                              (implicit parameters: ClauseCompletionParameters): Unit
}
