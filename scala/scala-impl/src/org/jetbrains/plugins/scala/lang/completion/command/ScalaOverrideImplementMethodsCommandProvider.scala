//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.commands.{ActionCommandProvider, ActionCompletionCommand}
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import java.util

abstract class ScalaOverrideImplementMethodsCommandProvider(
  actionId: String,
  presentableName: String,
  previewText: String,
  synonyms: util.List[String]
) extends ActionCommandProvider(
  actionId,
  presentableName,
  /*icon*/ null,
  /*priority*/ null,
  previewText,
  synonyms
) {
  @Nullable
  private def getTargetElement(psiFile: PsiFile, offset: Int) = getIdentifierOrNameIdCommandContext(psiFile, offset) match {
    case element @ Parent(td: ScTypeDefinition) if td.extendsBlock.templateParents.exists(_.allTypeElements.nonEmpty) => element
    case _ => null
  }

  override def isApplicable(offset: Int, psiFile: PsiFile, editor: Editor): Boolean = {
    val target = getTargetElement(psiFile, offset)
    target != null
  }

  override def createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand = new ActionCompletionCommand(
    getActionId,
    getPresentableName,
    getPreviewText,
    getIcon,
    getPriority,
    /*highlightInfo*/ null,
    getSynonyms
  ) {
    override def execute(offset: Int, psiFile: PsiFile, editor: Editor): Unit = {
      val target = getTargetElement(psiFile, offset)
      if (target == null) return
      val targetOffset = target.getTextRange.getStartOffset
      val fileDocument = psiFile.getFileDocument
      val rangeMarker = fileDocument.createRangeMarker(offset, offset)
      if (editor != null) editor.getCaretModel.moveToOffset(targetOffset)
      super.execute(offset, psiFile, editor)
      if (rangeMarker.isValid && editor != null) {
        editor.getCaretModel.moveToOffset(rangeMarker.getStartOffset)
      }
    }
  }
}

final class ScalaOverrideMethodsCommandProvider extends ScalaOverrideImplementMethodsCommandProvider(
  "OverrideMethods",
  ActionsBundle.message("action.OverrideMethods.text"),
  ActionsBundle.message("action.OverrideMethods.description"),
  util.List.of("Override methods")
)

final class ScalaImplementMethodsCommandProvider extends ScalaOverrideImplementMethodsCommandProvider(
  "ImplementMethods",
  ActionsBundle.message("action.ImplementMethods.text"),
  ActionsBundle.message("action.ImplementMethods.description"),
  util.List.of("Implement methods")
)
