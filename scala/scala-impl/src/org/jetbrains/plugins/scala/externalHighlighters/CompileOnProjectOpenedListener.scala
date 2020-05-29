package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}

class CompileOnProjectOpenedListener
  extends ProjectManagerListener {
  
  override def projectOpened(project: Project): Unit = if (!ApplicationManager.getApplication.isUnitTestMode) {
    DumbService.getInstance(project).runWhenSmart { () =>
      if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) 
        JpsCompiler.get(project).rescheduleCompilation(testScopeOnly = false)
    }
  }
}
