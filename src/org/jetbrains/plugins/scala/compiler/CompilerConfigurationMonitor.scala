package org.jetbrains.plugins.scala
package compiler

import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.compiler.{CompileContext, CompileTask, CompilerManager, CompilerMessageCategory}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project._

/**
 * @author Pavel Fatin
 */
class CompilerConfigurationMonitor(project: Project) extends ProjectComponent {
  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {
    def execute(context: CompileContext): Boolean = {
      if (isScalaProject && isCompileServerEnabled && isAutomakeEnabled) {
        val message = "Automake is not supported with Scala compile server. " +
                "Please either disable the compile server or turn off \"Project Setings / Compiler / Make project automatically\"."
        context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1)
        false
      } else {
        true
      }
    }
  })

  private def compilerConfiguration = CompilerWorkspaceConfiguration.getInstance(project)

  private def compileServerConfiguration = ScalaApplicationSettings.getInstance

  private def isScalaProject = project.hasScala

  private def isCompileServerEnabled = compileServerConfiguration.COMPILE_SERVER_ENABLED

  private def isAutomakeEnabled = compilerConfiguration.MAKE_PROJECT_ON_SAVE

  def getComponentName = getClass.getSimpleName

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {
    project.scalaEvents.addScalaProjectListener(ScalaListener)

    if (isScalaProject && isCompileServerEnabled) {
      disableAutomake()
    }
  }

  def projectClosed() {
    project.scalaEvents.removeScalaProjectListener(ScalaListener)
  }

  private def disableAutomake() {
    compilerConfiguration.MAKE_PROJECT_ON_SAVE = false
  }

  private object ScalaListener extends ScalaProjectListener {
    def onScalaAdded() {
      if (isCompileServerEnabled) {
        disableAutomake()
      }
    }

    def onScalaRemoved() {}
  }
}