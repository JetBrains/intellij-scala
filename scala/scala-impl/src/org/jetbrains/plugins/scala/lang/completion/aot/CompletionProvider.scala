package org.jetbrains.plugins.scala.lang.completion.aot

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionResultSet}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.{DelegatingCompletionProvider, positionFromParameters}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

private[completion] trait CompletionProvider[E <: ScalaPsiElement] extends DelegatingCompletionProvider[E] {

  override protected def addCompletions(resultSet: CompletionResultSet,
                                        prefix: String)
                                       (implicit parameters: CompletionParameters,
                                        context: ProcessingContext): Unit = positionFromParameters match {
    case position if ScalaProjectSettings.getInstance(position.getProject).isAotCompletion =>
      val replacement = createElement(
        Delimiter + StringUtil.capitalize(position.getText),
        prefix,
        position
      )

      val context = findContext(replacement)
      replacement.context = context
      replacement.child = context.getLastChild

      val Some(typeElement) = findTypeElement(replacement)
      val newParameters = createParameters(typeElement, Some(prefix.length))
      createConsumer(resultSet, position).runRemainingContributors(newParameters)
    case _ =>
  }

  protected def findTypeElement(element: E): Option[ScTypeElement]

  protected def findContext(element: E): PsiElement = element.getContext.getContext

  override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): Consumer
}
