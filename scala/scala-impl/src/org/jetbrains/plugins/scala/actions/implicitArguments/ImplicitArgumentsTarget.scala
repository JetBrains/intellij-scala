package org.jetbrains.plugins.scala.actions.implicitArguments

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private case class ImplicitArgumentsTarget(expression: PsiElement,
                                           arguments: collection.Seq[ScalaResolveResult],
                                           implicitConversion: Option[ScalaResolveResult] = None) {

  def presentation: String = {
    val shortenedText = expression match {
      case e: ScExpression => ScalaRefactoringUtil.getShortText(e)
      case _               => expression.getText.take(20)
    }
    implicitConversion match {
      case None    => shortenedText
      case Some(c) => c.element.name + s"($shortenedText) // implicit conversion"
    }
  }
}
