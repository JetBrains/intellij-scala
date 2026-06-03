package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractGoToDeclarationCompletionCommandProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

//noinspection UnstableApiUsage
final class ScalaGoToDeclarationCommandCompletionProvider extends AbstractGoToDeclarationCompletionCommandProvider {
  override def canNavigateToDeclaration(context: PsiElement): Boolean =
    if (context.elementType != ScalaTokenTypes.tIDENTIFIER) false
    else {
      val ref = context.parentOfType[ScReference]
      ref.exists(_.resolve() != null)
    }
}
