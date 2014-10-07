package org.jetbrains.plugins.scala
package debugger.evaluation

import java.io.File

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.{DebuggerSession, DebuggerManagerAdapter}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.{RemoteServerConnectorBase, ScalaApplicationSettings}

/**
 * Nikolay.Tropin
 * 2014-10-07
 */
class ScalaEvaluatorCompiler(project: Project) extends AbstractProjectComponent(project) {

  def isCompileServerEnabled = ScalaApplicationSettings.getInstance().COMPILE_SERVER_ENABLED

  override def projectOpened(): Unit = {
    DebuggerManagerEx.getInstanceEx(project).addDebuggerManagerListener(
      new DebuggerManagerAdapter {
        override def sessionAttached(session: DebuggerSession): Unit = runServer()

        override def sessionDetached(session: DebuggerSession) = stopServer()
      }
    )
  }

  def runServer() {}

  def stopServer() {}
}

class ServerConnector(module: Module, file: File, outputDir: File) extends RemoteServerConnectorBase(module, file, outputDir) {


}
