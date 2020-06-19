package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}

/**
 * Sets Scala Compile Server's JDK to the build process.
 *
 * @see SCL-17676
 */
class SetSameJdkToBuildProcessAsInCompileServer
  extends CompileTask {

  override def execute(context: CompileContext): Boolean = {
    val project = context.getProject
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
      setBuildProcessJdk(project)
    true
  }

  private def setBuildProcessJdk(project: Project): Unit = {
    val compileServerJdkHome = for {
      jdk <- getCompileServerSdk(project)
      jdkHome <- Option(jdk.getHomePath)
    } yield jdkHome
    Registry.get("compiler.process.jdk").setValue(compileServerJdkHome.getOrElse(""))
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
