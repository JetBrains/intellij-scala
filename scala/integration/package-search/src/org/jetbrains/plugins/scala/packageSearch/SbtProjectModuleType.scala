package org.jetbrains.plugins.scala.packageSearch

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{ProjectModuleType, ProjectModuleTypeTerm}



import java.util
import javax.swing.Icon

import org.jetbrains.plugins.scala.icons.Icons

import scala.jdk.CollectionConverters._

object SbtProjectModuleType extends ProjectModuleType{
  override def getIcon: Icon = Icons.SBT

  override def getPackageIcon: Icon = Icons.SBT

  override def defaultScope(project: Project): String = SbtObjects.defaultLibConfiguration

  override def scopes(project: Project): util.List[String] = {
    SbtObjects.libConfigurations.split(",").toList.asJava
  }

  override def terminologyFor(projectModuleTypeTerm: ProjectModuleTypeTerm): String = SbtObjects.configurationTerminology

}
