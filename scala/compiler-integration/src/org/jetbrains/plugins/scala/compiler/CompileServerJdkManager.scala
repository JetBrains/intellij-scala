package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

object CompileServerJdkManager {

  def compileServerJdk(project: Project): Option[Jdk] =
    for {
      sdk <- CompileServerLauncher.compileServerSdk(project).toOption
      version <- getJdkVersion(sdk)
    } yield (sdk, version)
  
  def recommendedJdk(project: Project): Jdk =
    getProjectJdk(project)
      .filter { case (_, version) =>
        if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
          // Compiler based highlighting is enabled and we need at least Java 11 to be able to execute the
          // JPS code inside the Scala Compile Server.
          version.isAtLeast(JavaSdkVersion.JDK_11)
        } else true
      }
      .getOrElse {
        // The project JDK cannot run the JPS code inside the Scala Compile Server (JDK version < 11).
        // Use the JDK which the JPS build system uses as a fallback. This is usually the bundled
        // JetBrains Runtime JDK.
        val fallback = getBuildProcessRuntimeJdk(project)
        (fallback.first, fallback.second)
      }

  /**
   * Returns the Build Process runtime SDK.
   * The method isn't thread-safe, so the synchronized is used.
   * @see SCL-17710
   */
  private def getBuildProcessRuntimeJdk(project: Project): Pair[Sdk, JavaSdkVersion] = synchronized {
    BuildManager.getBuildProcessRuntimeSdk(project)
  }
  
  private def getProjectJdk(project: Project): Option[Jdk] =
    for {
      sdk <- Option(ProjectRootManager.getInstance(project).getProjectSdk)
      version <- getJdkVersion(sdk)
    } yield (sdk, version)

  private def getJdkVersion(sdk: Sdk): Option[JavaSdkVersion] =
    Option(sdk.getSdkType).collect {
      case javaType: JavaSdk => javaType.getVersion(sdk)
    }

  type Jdk = (Sdk, JavaSdkVersion)
}
