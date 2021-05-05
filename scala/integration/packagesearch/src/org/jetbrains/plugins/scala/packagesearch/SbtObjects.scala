package org.jetbrains.plugins.scala.packagesearch

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{BuildSystemType, ProjectModuleType, ProjectModuleTypeTerm}
import org.jetbrains.plugins.scala.icons.Icons

import java.util
import javax.swing.Icon

import scala.jdk.CollectionConverters._

object SbtCommon {
  val buildSystemType = new BuildSystemType("SBT", "sbt")
//  val libConfigurations = "compile,test,runtime,integrationtest,default,provided,optional"
  val libScopes = "Compile,Test"
  val defaultLibScope = "Compile"
  val scopeTerminology = "Configuration"
  def buildScalaDependencyString(artifactID: String, scalaVer: String): String = {
    val ver = scalaVer.split('.')
    s"${artifactID}_${ver(0)}.${ver(1)}"
  }
}

object SbtProjectModuleType extends ProjectModuleType{
  override def getIcon: Icon = Icons.SBT

  override def getPackageIcon: Icon = Icons.SBT

  override def defaultScope(project: Project): String = SbtCommon.defaultLibScope

  override def scopes(project: Project): util.List[String] = {
    SbtCommon.libScopes.split(",").toList.asJava
  }

  override def terminologyFor(projectModuleTypeTerm: ProjectModuleTypeTerm): String = SbtCommon.scopeTerminology

}
