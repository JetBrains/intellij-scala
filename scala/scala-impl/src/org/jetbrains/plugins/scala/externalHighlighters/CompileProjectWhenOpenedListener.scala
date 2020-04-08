package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode

class CompileProjectWhenOpenedListener
  extends ProjectManagerListener {
  
  override def projectOpened(project: Project): Unit =
    if (!ApplicationManager.getApplication.isUnitTestMode)
      DumbService.getInstance(project).runWhenSmart { () =>
        if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
          CompilerManager.getInstance(project).make(null)
      }
}
