package org.jetbrains.plugins.scala.packagesearch.utils

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{ProjectModuleType, ProjectModuleTypeTerm}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.language.utils.SbtDependencyCommon

import java.util
import javax.swing.Icon
import scala.jdk.CollectionConverters._

object SbtProjectModuleType extends ProjectModuleType{
  override def getIcon: Icon = Icons.SBT

  override def getPackageIcon: Icon = Icons.SBT

  override def defaultScope(project: Project): String = SbtDependencyCommon.defaultLibScope

  override def userDefinedScopes(project: Project): util.List[String] = {
    SbtDependencyCommon.libScopes.split(",").toList.asJava
  }

  override def terminologyFor(projectModuleTypeTerm: ProjectModuleTypeTerm): String = SbtDependencyCommon.scopeTerminology

}
