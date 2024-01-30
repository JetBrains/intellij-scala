package org.jetbrains.plugins.scala

import com.intellij.compiler.server.BuildManager
import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher.CompileServerProblem

import java.io.File

package object compiler {
  case class JDK(executable: File, tools: Option[File], name: String, version: Option[JavaSdkVersion])

  def toJdk(sdk: Sdk): Either[CompileServerProblem, JDK] = sdk.getSdkType match {
    case jdkType: JavaSdk =>
      val vmExecutable = new File(jdkType.getVMExecutablePath(sdk))
      val tools = Option(jdkType.getToolsPath(sdk)).map(new File(_)) // TODO properly handle JDK 6 on Mac OS
      val version = Option(jdkType.getVersion(sdk))
      Right(JDK(vmExecutable, tools, sdk.getName, version))
    case unexpected =>
      Left(CompileServerProblem.Error(CompilerIntegrationBundle.message("unexpected.sdk.type.for.sdk", unexpected, sdk)))
  }

  class OpenScalaCompileServerSettingsAction(project: Project, filter: String) extends NotificationAction(CompilerIntegrationBundle.message("wrong.jdk.action.open.compile.server.settings")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      notification.expire()
      CompileServerManager.showCompileServerSettingsDialog(project, filter)
    }
  }

  def executeOnBuildThread(runnable: Runnable): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      runnable.run()
    } else {
      BuildManager.getInstance().runCommand(runnable)
    }
  }
}
