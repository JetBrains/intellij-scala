package org.jetbrains.plugins.scala
package compiler

import config.ScalaFacet
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import com.intellij.facet.{ProjectWideFacetListenersRegistry, ProjectWideFacetAdapter}
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.compiler.{CompilerMessageCategory, CompileContext, CompileTask, CompilerManager}

/**
 * @author Pavel Fatin
 */
class CompilerConfigurationMonitor(project: Project) extends ProjectComponent {
  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {
    def execute(context: CompileContext): Boolean = {
      val scalaProject = isScalaProject
      val automake = compilerConfiguration.MAKE_PROJECT_ON_SAVE
      val parallelCompilation = compilerConfiguration.PARALLEL_COMPILATION

      if (scalaProject) {
        if (automake) {
          val message = "Automake is not supported for Scala projects. " +
                  "Please turn off \"Project Setings / Compiler / Make project automatically\"."
          context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1)
        }

        if (parallelCompilation) {
          val message = "Parallel compilation is not supported for Scala projects. " +
                  "Please turn off \"Project Setings / Compiler / Compile independent modules in parallel\"."
          context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1)
        }
      }

      !(scalaProject && (automake || parallelCompilation))
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
      disableAutomake()
      disableParallelCompilation()
    }
  }

  def projectClosed() {
    registry.unregisterListener(ScalaFacet.Id, FacetListener)
  }

  private def disableAutomake() {
    compilerConfiguration.MAKE_PROJECT_ON_SAVE = false
  }

  private def disableParallelCompilation() {
    compilerConfiguration.PARALLEL_COMPILATION = false
  }

  private object FacetListener extends ProjectWideFacetAdapter[ScalaFacet]() {
    override def facetAdded(facet: ScalaFacet) {
      disableAutomake()
      disableParallelCompilation()
    }
  }
}