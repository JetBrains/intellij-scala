//noinspection UnstableApiUsage
package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.commands.{ActionCommandProvider, ActionCompletionCommand}
import com.intellij.codeInsight.completion.command.{CommandCompletionProviderContext, HighlightInfoLookup}
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

import scala.jdk.CollectionConverters.SeqHasAsJava

abstract class AbstractIntroduceCompletionCommandProvider(
  @Language("devkit-action-id") actionId: String,
  @Nls presentableName: String,
  @Nls previewText: String,
  synonyms: Seq[String],
) extends ActionCommandProvider(actionId, presentableName, null, -150, previewText, synonyms.asJava) {
  @Nullable
  protected def findTargetRange(offset: Int, file: PsiFile): TextRange = {
    val expressions = ScalaRefactoringUtil.possibleExpressionsToExtract(file, offset)
    expressions.headOption.map(_.getTextRange).orNull
  }

  override def isApplicable(offset: Int, psiFile: PsiFile, editor: Editor): Boolean =
    super.isApplicable(offset, psiFile, editor) && findTargetRange(offset, psiFile) != null

  override def createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand = {
    val targetRange = findTargetRange(context.getOffset, context.getPsiFile)
    if (targetRange == null) return null

    new ActionCompletionCommand(getActionId,
      getPresentableName,
      getPreviewText,
      getIcon,
      getPriority,
      new HighlightInfoLookup(targetRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0),
      getSynonyms
    ) {
      override def execute(offset: Int, psiFile: PsiFile, editor: Editor): Unit =
        if (offset < 1 || !targetRange.shiftRight(1).contains(offset))
          super.execute(offset, psiFile, editor)
        else {
          val fileDocument = psiFile.getFileDocument
          targetRange.containsOffset(offset)
          val rangeMarker = fileDocument.createRangeMarker(offset, offset)
          // `foo(bar.baz<caret>)` selects the whole `foo(...)` expression even though the caret is technically on `baz`
          // if we move the caret one character back, "introduce..." action will suggest `bar.baz` and `foo(...)` expressions
          moveToOffset(editor, offset - 1)
          super.execute(offset, psiFile, editor)
          if (rangeMarker.isValid) {
            moveToOffset(editor, rangeMarker.getStartOffset)
          }
        }

      private def moveToOffset(@Nullable editor: Editor, offset: Int): Unit = if (editor != null) {
        editor.getCaretModel.moveToOffset(offset)
      }
    }
  }
}

final class ScalaIntroduceVariableCompletionCommandProvider extends AbstractIntroduceCompletionCommandProvider(
  actionId = "IntroduceVariable",
  presentableName = ActionsBundle.message("action.IntroduceVariable.text"),
  previewText = ActionsBundle.message("action.IntroduceVariable.description"),
  synonyms = Seq("Introduce variable", "Extract variable")
)

final class ScalaIntroduceFieldCompletionCommandProvider extends AbstractIntroduceCompletionCommandProvider(
  actionId = "IntroduceField",
  presentableName = ActionsBundle.message("action.IntroduceField.text"),
  previewText = ActionsBundle.message("action.IntroduceField.description"),
  synonyms = Seq("Introduce field", "Extract field")
)

final class ScalaIntroduceParameterCompletionCommandProvider extends AbstractIntroduceCompletionCommandProvider(
  actionId = "IntroduceParameter",
  presentableName = ActionsBundle.message("action.IntroduceParameter.text"),
  previewText = ActionsBundle.message("action.IntroduceParameter.description"),
  synonyms = Seq("Introduce parameter", "Extract parameter")
)
