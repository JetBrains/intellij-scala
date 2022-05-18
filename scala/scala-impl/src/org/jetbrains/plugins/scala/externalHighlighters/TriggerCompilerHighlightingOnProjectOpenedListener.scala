package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}
import org.jetbrains.plugins.scala.util.ScalaUtil

class TriggerCompilerHighlightingOnProjectOpenedListener
  extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit =
    if (!ApplicationManager.getApplication.isUnitTestMode && !LightEdit.owns(project)) {
      DumbService.getInstance(project).runWhenSmart { () =>
        if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
          TriggerCompilerHighlightingService.get(project).showErrorsFromCompilerEnabledAtLeastForOneOpenEditor match {
            case Some(editor) =>
              val editedFile = Option(editor.getFile)
              val fileName = editedFile.map(_.getName).getOrElse("<no file>")
              val modules = editedFile
                .flatMap(ScalaUtil.getModuleForFile(_)(project)).map(Seq(_))
                .getOrElse(ScalaHighlightingMode.compilerBasedHighlightingModules(project))
              CompilerHighlightingService.get(project).triggerIncrementalCompilation(s"project opened, editor with scala3 exists: $fileName", modules, delayedProgressShow = false)
            case _ =>
          }
        }
      }
    }
}