//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.PreviewUtilsKt.tryToCalculateCommandCompletionPreview
import com.intellij.codeInsight.completion.command.{CommandCompletionProviderContext, CommandProvider, CompletionCommand, HighlightInfoLookup}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

import java.util

@ApiStatus.Internal
abstract class ScalaIntentionOnIdentifierCommandProvider extends CommandProvider {
  def getPriority: Int = -150

  def getSynonyms: util.List[String]

  def getIntention: IntentionAction

  override def getCommands(context: CommandCompletionProviderContext): util.List[CompletionCommand] = {
    val target = getIdentifierOrNameIdCommandContext(context.getPsiFile, context.getOffset)
    if (target == null) return util.Collections.emptyList()
    val range = target.getTextRange

    val intention = getIntention
    val available = ShowIntentionActionsHandler.availableFor(
      context.getOriginalPsiFile,
      context.getOriginalEditor,
      range.getStartOffset,
      intention
    )
    if (!available) return util.Collections.emptyList()

    val command = new MyCommand(context, range, getPriority, getSynonyms, intention)
    util.Collections.singletonList(command)
  }

  private final class MyCommand(
    context: CommandCompletionProviderContext,
    range: TextRange,
    priority: Int,
    synonyms: util.List[String],
    intention: IntentionAction,
  ) extends CompletionCommand {
    override def getPresentableName: String = intention.getFamilyName

    override def getSynonyms: util.List[String] = synonyms

    override def getPriority: Integer = priority

    override def getHighlightInfo: HighlightInfoLookup =
      new HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)

    override def execute(offset: Int, psiFile: PsiFile, editor: Editor): Unit = {
      moveCaretToStart(editor)
      ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, intention, getPresentableName)
    }

    private def moveCaretToStart(editor: Editor): Unit =
      editor.getCaretModel.moveToOffset(range.getStartOffset)

    override def getPreview: IntentionPreviewInfo = tryToCalculateCommandCompletionPreview(
      (editor, file, _) => {
        moveCaretToStart(editor)
        intention.invoke(file.getProject, editor, file)
        val origText = context.getPsiFile.getText
        val modifiedText = file.getText
        if (origText == modifiedText) IntentionPreviewInfo.EMPTY
        else new IntentionPreviewInfo.CustomDiff(context.getPsiFile.getFileType, null, origText, modifiedText, true)
      },
      context,
      (_, _, _) => true,
      () => super.getPreview
    )
  }
}
