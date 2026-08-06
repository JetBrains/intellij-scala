package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractTypeHierarchyCompletionCommandProvider
import com.intellij.psi.{PsiClass, PsiElement, PsiFile, PsiMethod}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions.{&, PsiElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinitionLike

//noinspection UnstableApiUsage
final class ScalaTypeHierarchyCompletionCommandProvider extends AbstractTypeHierarchyCompletionCommandProvider {
  override def findElement(offset: Int, psiFile: PsiFile): PsiElement = {
    val element = getNonWhitespaceCommandContext(psiFile, offset)

    if (element == null) null
    else if (isConstructorOrSelfInvocation(element)) element
    else if (isClassOrResolvesToClass(element)) element
    else null
  }

  private def isConstructorOrSelfInvocation(@NotNull element: PsiElement): Boolean =
    if (element.elementType != ScalaTokenTypes.kTHIS) false
    else element.getParent match {
      case m: PsiMethod => m.isConstructor
      case _: ScSelfInvocation => true
      case _ => false
    }

  private def isClassOrResolvesToClass(@NotNull element: PsiElement): Boolean =
    if (element.elementType != ScalaTokenTypes.tIDENTIFIER) false
    else element.getParent match {
      case _: PsiClass => true
      case (_: ScReference) & ResolvesTo(_: PsiClass | _: ScTypeDefinitionLike | _: ScPrimaryConstructor) => true
      case _ => false
    }
}
