package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.registry.{Registry, RegistryValue}
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Sets Scala Compile Server's JDK to the Build Process.
 *
 * For Scala projects it sets the 'compiler.process.jdk' property value to JDK used in Compile Server.
 * For Non-scala projects it erases the 'compiler.process.jdk' value.
 *
 * @see SCL-17676
 */
class SetSameJdkToBuildProcessAsInCompileServer
  extends CompileTask
    with BuildManagerListener {

  // BEFORE
  override def execute(context: CompileContext): Boolean = {
    TempCompilerProcessJdkService.get().overrideOrEraseBuildProcessJdk(context.getProject)
    true
  }
}

class SetSameJdkToBuildProcessAsInCompileServerProjectManagerListener
  extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit =
    TempCompilerProcessJdkService.get().overrideOrEraseBuildProcessJdk(project)
}

@Service
final class TempCompilerProcessJdkService
  extends Disposable {

  private def registryValue: RegistryValue =
    Registry.get("compiler.process.jdk")

  def overrideBuildProcessJdk(project: Project): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
      setTempCompilerProcessJdk(getCompileServerJdkHome(project))

  def eraseBuildProcessJdk(): Unit =
    registryValue.setValue("")

  def overrideOrEraseBuildProcessJdk(project: Project): Unit = if (project.hasScala)
    overrideBuildProcessJdk(project)
  else
    eraseBuildProcessJdk()

  private def setTempCompilerProcessJdk(tempJdkHome: => String): Unit =
    registryValue.setValue(tempJdkHome)

  private def getCompileServerJdkHome(project: Project): String = {
    val compileServerJdkHome = for {
      jdk <- getCompileServerSdk(project)
      jdkHome <- Option(jdk.getHomePath)
    } yield jdkHome
    compileServerJdkHome.getOrElse("")
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

  override def dispose(): Unit =
    eraseBuildProcessJdk()
}

object TempCompilerProcessJdkService {

  def get(): TempCompilerProcessJdkService =
    ServiceManager.getService(classOf[TempCompilerProcessJdkService])
}
