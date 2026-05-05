package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractParameterInfoCompletionCommand
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeArgs}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScGenericCall, ScMethodCall}

//noinspection UnstableApiUsage
final class ScalaParameterInfoCompletionCommand extends AbstractParameterInfoCompletionCommand {
  override def inParameterList(offset: Int, psiFile: PsiFile): Boolean = {
    val element = psiFile.findElementAt(offset)
    if (element == null) return false
    element.parentOfType(Seq(classOf[ScArgumentExprList], classOf[ScTypeArgs])).exists {
      case (argList: ScArgumentExprList) & Parent(_: ScMethodCall | _: ScConstructorInvocation) =>
        isInRange(argList, offset)
      case (typeArgs: ScTypeArgs) & Parent(_: ScGenericCall | _: ScParameterizedTypeElement) =>
        isInRange(typeArgs, offset)
      case _ => false
    }
  }

  private def isInRange(element: PsiElement, offset: Int): Boolean = {
    val range = element.getTextRange
    range.getStartOffset < offset && offset < range.getEndOffset
  }
}
