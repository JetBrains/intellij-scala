package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}

class TriggerCompilerHighlightingOnProjectOpenedListener
  extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit =
    if (!ApplicationManager.getApplication.isUnitTestMode && !LightEdit.owns(project)) {
      DumbService.getInstance(project).runWhenSmart { () =>
        if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
          TriggerCompilerHighlightingService.get(project).showErrorsFromCompilerEnabledAtLeastForOneOpenEditor match {
            case Some(editor) =>
              val fileName = Option(editor.getFile).map(_.getName).getOrElse("<no file>")
              CompilerHighlightingService.get(project).triggerIncrementalCompilation(s"project opened, editor with scala3 exists: $fileName", delayedProgressShow = false)
            case _ =>
          }
        }
      }
    }
}