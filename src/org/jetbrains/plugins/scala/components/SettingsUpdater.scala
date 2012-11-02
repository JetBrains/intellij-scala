package org.jetbrains.plugins.scala
package components

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import config.ScalaFacet
import com.intellij.facet.{ProjectWideFacetListenersRegistry, ProjectWideFacetAdapter}
import org.jetbrains.plugins.scala.compiler.ScalacBackendCompiler
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.compiler.{CompilerMessageCategory, CompileContext, CompileTask, CompilerManager}

/**
 * Pavel Fatin
 */

class SettingsUpdater(project: Project) extends ProjectComponent {
  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {
    def execute(context: CompileContext): Boolean = {
      val unsupported = isScalaProject && compilerConfiguration.USE_COMPILE_SERVER
      if (unsupported) {
        val message = "External Scala compiler is not supported yet. Please turn off \"Project Setings / Compiler / Use external build\"."
        context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1)
      }
      !unsupported
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

    if (isScalaProject) {
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
      disableExternalCompiler()
    }
  }
}