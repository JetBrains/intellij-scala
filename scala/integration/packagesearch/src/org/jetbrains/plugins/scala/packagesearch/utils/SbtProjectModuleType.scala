package org.jetbrains.plugins.scala.packagesearch.utils

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import org.jetbrains.sbt.icons.Icons
import org.jetbrains.sbt.language.utils.SbtDependencyCommon

import java.util
import javax.swing.Icon
import scala.jdk.CollectionConverters._

object SbtProjectModuleType extends ProjectModuleType {
  override def getIcon: Icon = Icons.SBT

  override def getPackageIcon: Icon = Icons.SBT

  override def defaultScope(project: Project): PackageScope =
    PackageScopeCompanionProxy.companion.from(SbtDependencyCommon.defaultLibScope)

  override def userDefinedScopes(project: Project): util.List[PackageScope] =
    SbtDependencyCommon.libScopes
      .split(",")
      .toList
      .map(PackageScopeCompanionProxy.companion.from)
      .asJava

}
