package org.jetbrains.plugins.scala.externalHighlighters

import java.util.UUID

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.registry.{Registry, RegistryValue}
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}

/**
 * Sets Scala Compile Server's JDK to the build process.
 * After build finished it resets the build process JDK value.
 *
 * @see SCL-17676
 */
class SetSameJdkToBuildProcessAsInCompileServer
  extends CompileTask
    with BuildManagerListener {

  import SetSameJdkToBuildProcessAsInCompileServer.previousJdkHome

  override def execute(context: CompileContext): Boolean = {
    val project = context.getProject
    val isUnitTestMode = ApplicationManager.getApplication.isUnitTestMode
    if (!isUnitTestMode && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
      setBuildProcessJdk(project)
    true
  }

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    previousJdkHome.foreach(registryValue.setValue)
    previousJdkHome = None
  }

  private def registryValue: RegistryValue =
    Registry.get("compiler.process.jdk")

  private def setBuildProcessJdk(project: Project): Unit = {
    previousJdkHome = Some(registryValue.asString)
    val compileServerJdkHome = for {
      jdk <- getCompileServerSdk(project)
      jdkHome <- Option(jdk.getHomePath)
    } yield jdkHome
    registryValue.setValue(compileServerJdkHome.getOrElse(""))
  }

  private def getCompileServerSdk(project: Project): Option[Sdk] = {
    val settings = ScalaCompileServerSettings.getInstance
    if (settings.COMPILE_SERVER_ENABLED)
      Option(
        if (settings.USE_DEFAULT_SDK)
          CompileServerLauncher.defaultSdk(project)
        else
          ProjectJdkTable.getInstance.findJdk(settings.COMPILE_SERVER_SDK)
      )
    else
      None
  }
}

object SetSameJdkToBuildProcessAsInCompileServer {

  @volatile private var previousJdkHome = Option.empty[String]
}
