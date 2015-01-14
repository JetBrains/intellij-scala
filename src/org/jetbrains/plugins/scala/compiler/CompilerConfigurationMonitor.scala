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

  private def compilerConfiguration = CompilerWorkspaceConfiguration.getInstance(project)

  private def compileServerConfiguration = ScalaCompileServerSettings.getInstance

  private def isScalaProject = project.hasScala

  private def isCompileServerEnabled = compileServerConfiguration.COMPILE_SERVER_ENABLED

  private def isAutomakeEnabled = compilerConfiguration.MAKE_PROJECT_ON_SAVE

  def getComponentName = getClass.getSimpleName

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {
    if (isAutomakeEnabled && isCompileServerEnabled && isScalaProject) {
      CompileServerLauncher.instance.tryToStart(project)
    }
  }

  def projectClosed() {}
}