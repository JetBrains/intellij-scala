package org.jetbrains.plugins.scala.externalHighlighters

import java.util.UUID

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.registry.{Registry, RegistryValue}
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}

/**
 * Sets Scala Compile Server's JDK to the Build Process. And then resets it to original value.
 *
 * The steps:
 * 1. Set the 'compiler.process.jdk' registry property to the Build Process BEFORE it is started.
 * 2. After the Build Process is stared, reset the property to the original value.
 * 3. Additionally reset the property value after build is finished. There are some cases when
 * [[buildStarted]] isn't invoked. But the [[buildFinished]] invocation is guaranteed. The "reset" operation is
 * idempotent, so that's OK.
 *
 * Note 1:
 * [[TempCompilerProcessJdkService]] is also resets the property value when disposed. We need this to
 * reset the property if user closes the project right after "BEFORE" is executed.
 *
 * Note 2:
 * If user kills the Idea process the property may be not resetted. There are no solutions for this problem.
 * But we hope this is a very rare situation.
 *
 * Note 3:
 * [[org.jetbrains.jps.api.BuildType.UP_TO_DATE_CHECK]] runs in JPS process. But for this build type
 * the platform doesn't invoke compiler task. So we also override JDK of the JPS-process after project was opened
 * (see [[SetSameJdkToBuildProcessAsInCompileServerProjectManagerListener]]).
 *
 * @see SCL-17676
 */
class SetSameJdkToBuildProcessAsInCompileServer
  extends CompileTask
    with BuildManagerListener {

  // BEFORE
  override def execute(context: CompileContext): Boolean = {
    TempCompilerProcessJdkService.get(context.getProject).overrideBuildProcessJdk()
    true
  }

  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    TempCompilerProcessJdkService.get(project).resetCompilerProcessJdk()

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    TempCompilerProcessJdkService.get(project).resetCompilerProcessJdk()
}

class SetSameJdkToBuildProcessAsInCompileServerProjectManagerListener
  extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit =
    TempCompilerProcessJdkService.get(project).overrideBuildProcessJdk()
}

@Service
final class TempCompilerProcessJdkService(project: Project)
  extends Disposable {

  import TempCompilerProcessJdkService._

  private def registryValue: RegistryValue =
    Registry.get("compiler.process.jdk")

  def overrideBuildProcessJdk(): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
      TempCompilerProcessJdkService.get(project).setTempCompilerProcessJdk(getCompileServerJdkHome)

  private def setTempCompilerProcessJdk(tempJdkHome: => String): Unit = lock.synchronized {
    if (originalJdkHome.isEmpty)
      originalJdkHome = Some(registryValue.asString)
    registryValue.setValue(tempJdkHome)
  }

  def resetCompilerProcessJdk(): Unit = lock.synchronized {
    originalJdkHome.foreach(registryValue.setValue)
    originalJdkHome = None
  }

  private def getCompileServerJdkHome: String = {
    val compileServerJdkHome = for {
      jdk <- getCompileServerSdk
      jdkHome <- Option(jdk.getHomePath)
    } yield jdkHome
    compileServerJdkHome.getOrElse("")
  }

  private def getCompileServerSdk: Option[Sdk] = {
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
    resetCompilerProcessJdk()
}

object TempCompilerProcessJdkService {

  private val lock = new Object
  @volatile private var originalJdkHome = Option.empty[String]

  def get(project: Project): TempCompilerProcessJdkService =
    ServiceManager.getService(project, classOf[TempCompilerProcessJdkService])
}
