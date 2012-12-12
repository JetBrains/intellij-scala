package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import config.ScalaFacet
import com.intellij.facet.{ProjectWideFacetListenersRegistry, ProjectWideFacetAdapter}
import org.jetbrains.plugins.scala.compiler.ScalacBackendCompiler
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.compiler.{CompilerMessageCategory, CompileContext, CompileTask, CompilerManager}
import com.intellij.openapi.application.ApplicationManager

/**
 * Pavel Fatin
 */

class SettingsUpdater(project: Project) extends ProjectComponent {
  private val ideaInternal = ApplicationManager.getApplication.isInternal

  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {
    var firstCompilation = true

    def execute(context: CompileContext): Boolean = {
      val scalaProject = isScalaProject

      val externalCompiler = compilerConfiguration.USE_COMPILE_SERVER

      if (firstCompilation && !ideaInternal && (scalaProject && externalCompiler)) {
        val message = "External Scala compiler is in experimental state, please use with care."
        context.addMessage(CompilerMessageCategory.WARNING, message, null, -1, -1)
      }

      if (scalaProject) {
        if (compilerConfiguration.USE_COMPILE_SERVER) {
          project.getComponent(classOf[FscServerLauncher]).stop()
          project.getComponent(classOf[FscServerManager]).removeWidget()

          val projectSettings = ScalacSettings.getInstance(context.getProject)

          if (projectSettings.COMPILATION_SERVER_ENABLED) {
            project.getComponent(classOf[CompilationServerManager]).configureWidget()
            project.getComponent(classOf[CompilationServerLauncher]).init()
          }
        } else {
          project.getComponent(classOf[CompilationServerLauncher]).stop()
          project.getComponent(classOf[CompilationServerManager]).removeWidget()
        }
      }

      firstCompilation = false

      true
    }
  })

  private def registry = ProjectWideFacetListenersRegistry.getInstance(project)

  private def compilerConfiguration = CompilerWorkspaceConfiguration.getInstance(project)

  private def isScalaProject = ScalacBackendCompiler.isScalaProject(ModuleManager.getInstance(project).getModules)

  def getComponentName = getClass.getSimpleName

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {
    registry.registerListener(ScalaFacet.Id, FacetListener)

    if (!ideaInternal && isScalaProject) {
      disableExternalCompiler()
    }
  }

  def projectClosed() {
    registry.unregisterListener(ScalaFacet.Id, FacetListener)
  }

  private def disableExternalCompiler() {
    compilerConfiguration.USE_COMPILE_SERVER = false
  }

  private object FacetListener extends ProjectWideFacetAdapter[ScalaFacet]() {
    override def facetAdded(facet: ScalaFacet) {
      if (!ideaInternal) disableExternalCompiler()
    }
  }
}