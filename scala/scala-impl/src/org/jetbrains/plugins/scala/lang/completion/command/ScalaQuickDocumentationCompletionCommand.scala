package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractQuickDocumentationCompletionCommand
import com.intellij.psi.{PsiElement, PsiFile, PsiMember, PsiPackage}
import org.jetbrains.plugins.scala.extensions.{&, PsiElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

//noinspection UnstableApiUsage
final class ScalaQuickDocumentationCompletionCommand extends AbstractQuickDocumentationCompletionCommand {
  override def findElement(offset: Int, psiFile: PsiFile): PsiElement = {
    val element = getNonWhitespaceCommandContext(psiFile, offset)
    if (element == null || element.elementType != ScalaTokenTypes.tIDENTIFIER) return null

    element.getParent match {
      case _: ScMember => element
      case (_: ScReference) & ResolvesTo(_: PsiMember | _: PsiPackage) => element
      case _ => null
    }
  }
}
