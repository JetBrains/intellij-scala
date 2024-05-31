package org.jetbrains.plugins.scala.lang.surroundWith

import com.intellij.lang.surroundWith.ModCommandSurrounder
import com.intellij.modcommand.{ActionContext, ModCommand, ModPsiUpdater}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

abstract class ScalaModCommandSurrounder extends ModCommandSurrounder {
  override final def surroundElements(context: ActionContext, elements: Array[PsiElement]): ModCommand =
    if (elements.isEmpty) ModCommand.nop()
    else ModCommand.psiUpdate(context, (updater: ModPsiUpdater) => {
      val range = surroundElements(elements.map(updater.getWritable), context)
      range.foreach(updater.select)
    })

  protected def surroundElements(elements: Array[PsiElement], context: ActionContext): Option[TextRange]
}
