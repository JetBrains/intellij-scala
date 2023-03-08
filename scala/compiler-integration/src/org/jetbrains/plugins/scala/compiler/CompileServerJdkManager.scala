package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

object CompileServerJdkManager {

  def compileServerJdk(project: Project): Option[Jdk] =
    for {
      sdk <- CompileServerLauncher.compileServerSdk(project).toOption
      version <- getJdkVersion(sdk)
    } yield (sdk, version)
  
  def recommendedJdk(project: Project): Jdk =
    getProjectJdk(project)
      .filter { case (_, version) => isCompatible(version) }
      .filter { case (_, version) => isRecommendedVersionForProject(project, version) }
      .getOrElse {
        // The project JDK cannot run the JPS code inside the Scala Compile Server (JDK version < 11).
        // Use the JDK which the JPS build system uses as a fallback. This is usually the bundled
        // JetBrains Runtime JDK.
        getBuildProcessRuntimeJdk(project)
      }

  /**
   * The Scala Compile Server code is compiled to Java 8 bytecode.
   */
  private[compiler] def isCompatible(version: JavaSdkVersion): Boolean = version.isAtLeast(JavaSdkVersion.JDK_1_8)

  private[compiler] def isRecommendedVersionForProject(project: Project, version: JavaSdkVersion): Boolean = {
    // Compiler based highlighting is enabled and we need at least Java 11 to be able to execute the
    // JPS code inside the Scala Compile Server.
    !ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project) || version.isAtLeast(JavaSdkVersion.JDK_11)
  }

  /**
   * Returns the Build Process runtime SDK.
   * The method isn't thread-safe, so the synchronized is used.
   * @see SCL-17710
   */
  private[compiler] def getBuildProcessRuntimeJdk(project: Project): Jdk = synchronized {
    val pair = BuildManager.getBuildProcessRuntimeSdk(project)
    (pair.first, pair.second)
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
