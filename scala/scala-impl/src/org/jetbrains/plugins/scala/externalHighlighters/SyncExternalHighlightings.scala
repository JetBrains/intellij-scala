package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}
import com.intellij.openapi.util.registry.{RegistryValue, RegistryValueListener}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Runs make or erases compiler-based highlighting when the value of
 * [[org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode.isShowErrorsFromCompilerEnabled]] probably changed.
 * 
 * In other words this listener ensures the consistency of the compiler highlighting.
 */
class SyncExternalHighlightings
  extends ProjectManagerListener {
  
  override def projectOpened(project: Project): Unit = if (!ApplicationManager.getApplication.isUnitTestMode) {
    compileOrEraseHighlightings(project)
    project.subscribeToModuleRootChanged() { _ => compileOrEraseHighlightings(project) }
    ScalaHighlightingMode.addRegistryListener(project)(new RegistryValueListener {
      override def afterValueChanged(value: RegistryValue): Unit = compileOrEraseHighlightings(project)
    })
  }
  
  private def compileOrEraseHighlightings(project: Project): Unit =
    DumbService.getInstance(project).runWhenSmart { () =>
      if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
        CompilerManager.getInstance(project).make(null)
      else
        ExternalHighlighters.eraseAllHighlightings(project)
    }
}
