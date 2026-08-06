//noinspection UnstableApiUsage
package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.CompletionCommandKt.getCommandContext
import com.intellij.codeInsight.completion.command.commands.{AbstractFormatCodeCompletionCommand, AbstractFormatCodeCompletionCommandProvider}
import com.intellij.codeInsight.completion.command.{CommandCompletionProviderContext, CompletionCommand, HighlightInfoLookup}
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

final class ScalaFormatCodeCompletionCommandProvider extends AbstractFormatCodeCompletionCommandProvider {
  override def createCommand(context: CommandCompletionProviderContext): CompletionCommand =
    getCommandContext(context.getOffset, context.getPsiFile) match {
      case null => null
      case element =>
        val targetElement = ScalaFormatCodeCompletionCommand.findTargetToRefactor(element)
        val highlightInfoLookup = new HighlightInfoLookup(targetElement.getTextRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
        val command = new ScalaFormatCodeCompletionCommand(context, highlightInfoLookup)
        command
    }
}

final class ScalaFormatCodeCompletionCommand(
  context: CommandCompletionProviderContext,
  @Nullable highlightInfo: HighlightInfoLookup
) extends AbstractFormatCodeCompletionCommand(context) {
  override def getHighlightInfo: HighlightInfoLookup = highlightInfo

  override def findTargetToRefactor(element: PsiElement): PsiElement =
    ScalaFormatCodeCompletionCommand.findTargetToRefactor(element)
}

object ScalaFormatCodeCompletionCommand {
  def findTargetToRefactor(element: PsiElement): PsiElement =
    element.withParentsInFile
      .filter(_.is[ScMember])
      .nextOption()
      .orElse(element.containingFile)
      .getOrElse(element)
}
