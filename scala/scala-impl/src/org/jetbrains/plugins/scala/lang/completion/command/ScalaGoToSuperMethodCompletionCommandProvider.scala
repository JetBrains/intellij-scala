package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.AbstractGoToSuperMethodCompletionCommandProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

//noinspection UnstableApiUsage
final class ScalaGoToSuperMethodCompletionCommandProvider extends AbstractGoToSuperMethodCompletionCommandProvider {
  override def canGoToSuperMethod(element: PsiElement, offset: Int): Boolean =
    element.parentOfType[ScFunction].exists { function =>
      val range = function.getTextRange
      range != null && range.contains(offset) && function.superMethods.nonEmpty
    }
}
