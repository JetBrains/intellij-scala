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
import org.jetbrains.plugins.scala.editor.documentationProvider.actions.CreateScalaDocStubIntentionAction

import java.util
import java.util.Collections

//noinspection ApiStatus,UnstableApiUsage
final class CreateScalaDocStubCommandProvider extends CommandProvider {
  override def getCommands(context: CommandCompletionProviderContext): util.List[CompletionCommand] = {
    val target = getIdentifierOrNameIdCommandContext(context.getPsiFile, context.getOffset)
    if (target == null) return Collections.emptyList()
    val range = target.getTextRange

    val intention = new CreateScalaDocStubIntentionAction
    val available = ShowIntentionActionsHandler.availableFor(
      context.getOriginalPsiFile,
      context.getOriginalEditor,
      range.getStartOffset,
      intention
    )
    if (!available) return Collections.emptyList()

    util.List.of(new AddScalaDocStubCommand(intention, range, context))
  }

  private final class AddScalaDocStubCommand(intention: IntentionAction, range: TextRange, context: CommandCompletionProviderContext) extends CompletionCommand {
    override def getPresentableName: String = intention.getFamilyName

    override def getSynonyms: util.List[String] = util.List.of("Add ScalaDoc")

    override def getPriority: Integer = -200

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
