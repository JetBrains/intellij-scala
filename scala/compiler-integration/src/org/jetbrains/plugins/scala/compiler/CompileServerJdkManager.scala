package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair

object CompileServerJdkManager {

  def compileServerJdk(project: Project): Option[Jdk] =
    for {
      sdk <- CompileServerLauncher.compileServerSdk(project).toOption
      version <- getJdkVersion(sdk)
    } yield (sdk, version)
  
  def recommendedJdk(project: Project): Option[Jdk] = {
    val jdk = getBuildProcessRuntimeJdk(project)
    Some((jdk.first, jdk.second))
  }

  final def recommendedSdk(project: Project): Option[Sdk] =
    recommendedJdk(project).map(_._1)

  final def getBuildProcessRuntimeSdk(project: Project): Sdk =
    getBuildProcessRuntimeJdk(project).first

  final def getBuildProcessJdkVersion(project: Project): JavaSdkVersion =
    compileServerJdk(project).map(_._2).getOrElse(getBuildProcessRuntimeJdk(project).second)

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
