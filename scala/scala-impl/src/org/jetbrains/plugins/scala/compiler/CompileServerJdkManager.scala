package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.{ModuleRootManager, ProjectRootManager}
import org.jetbrains.plugins.scala.project.ProjectExt

object CompileServerJdkManager {

  def compileServerJdk(project: Project): Option[Jdk] =
    for {
      sdk <- CompileServerLauncher.compileServerSdk(project).toOption
      version <- getJdkVersion(sdk)
    } yield (sdk, version)
  
  def recommendedJdk(project: Project): Option[Jdk] =
    getMaxJdkUsedInProject(project)
  
  final def recommendedSdk(project: Project): Option[Sdk] =
    recommendedJdk(project).map(_._1)

  private def getMaxJdkUsedInProject(project: Project): Option[Jdk] = {
    val oldestPossibleVersion = JavaSdkVersion.JDK_1_8
    val modulesJdks = project.modules.flatMap(getModuleJdk).toSet
    val projectJdk = getProjectJdk(project)
    
    val jdks = (modulesJdks ++ projectJdk).filter(x => x._2.isAtLeast(oldestPossibleVersion))
    if (jdks.nonEmpty) Some(jdks.maxBy(_._2)) else None
  }
  
  private def getProjectJdk(project: Project): Option[Jdk] =
    for {
      sdk <- Option(ProjectRootManager.getInstance(project).getProjectSdk)
      version <- getJdkVersion(sdk)
    } yield (sdk, version)

  private def getModuleJdk(module: Module): Option[Jdk] =
    for {
      sdk <- Option(ModuleRootManager.getInstance(module).getSdk)
      version <- getJdkVersion(sdk)
    } yield (sdk, version)

  private def getJdkVersion(sdk: Sdk): Option[JavaSdkVersion] =
    Option(sdk.getSdkType).collect {
      case javaType: JavaSdk => javaType.getVersion(sdk)
    }

  type Jdk = (Sdk, JavaSdkVersion)
}
