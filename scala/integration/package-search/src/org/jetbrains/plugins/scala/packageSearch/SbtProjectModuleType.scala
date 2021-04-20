package org.jetbrains.plugins.scala.packageSearch

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{ProjectModuleType, ProjectModuleTypeTerm}



import java.util
import javax.swing.Icon

import org.jetbrains.plugins.scala.icons.Icons

import scala.jdk.CollectionConverters._

class SbtProjectModuleType extends ProjectModuleType{
  override def getIcon: Icon = Icons.SBT

  override def getPackageIcon: Icon = Icons.SBT

  override def defaultScope(project: Project): String = "compile"

  override def scopes(project: Project): util.List[String] = {
    List("compile", "runtime", "test", "provided").asJava
  }

  override def terminologyFor(projectModuleTypeTerm: ProjectModuleTypeTerm): String = "configuration"
}
