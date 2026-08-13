package org.jetbrains.plugins.scala.runner

import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info
import com.intellij.execution.lineMarker.{ExecutorAction, RunLineMarkerContributor}
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.runner.ScalaRunLineMarkerContributor.RunIcon
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

import javax.swing.Icon

class ScalaRunLineMarkerContributor extends RunLineMarkerContributor {
  override def getInfo(element: PsiElement): Info = {
    val file = element.getContainingFile
    val project = if (file != null) file.getProject else element.getProject // Avoid tree walk-up

    if (!ScalaProjectSettings.in(project).isDisableInspections) {
      if (!element.isVisible(project, file)) return null
    }

    file match {
      case scriptLikeFile: ScalaFile
        if scriptLikeFile.isWorksheetFile || scriptLikeFile.isMultipleDeclarationsAllowed =>
        return null
      case _ =>
    }

    if (ScalaMainMethodUtil.hasMain(element))
      new Info(RunIcon, ExecutorAction.getActions(0), null)
    else
      null
  }
}

object ScalaRunLineMarkerContributor {
  val RunIcon: Icon = AllIcons.RunConfigurations.TestState.Run
}
