package org.jetbrains.plugins.scala.packageSearch

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.{UnifiedDependency, UnifiedDependencyRepository}
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.{module => OpenapiModule}
import org.jetbrains.sbt.SbtUtil

import java.util
import scala.jdk.CollectionConverters._

class SbtDependencyModificator extends ExternalDependencyModificator{
  override def supports(module: OpenapiModule.Module): Boolean = SbtUtil.isSbtModule(module)

  override def addDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency): Unit = {

  }

  override def updateDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency, unifiedDependency1: UnifiedDependency): Unit = {

  }

  override def removeDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency): Unit = {

  }

  override def addRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }

  override def deleteRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }

  override def declaredDependencies(module: OpenapiModule.Module): util.List[DeclaredDependency] = {
    val project = module.getProject
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module)
    val dataNodes = SbtUtil.getModuleData(project, moduleId, ProjectKeys.LIBRARY_DEPENDENCY)

    dataNodes.map(libDependencyData => {

      val dataContext = new DataContext {
        override def getData(dataId: String): AnyRef = null
      }
      
      val libs = libDependencyData.getExternalName.split(":")
      libs.length match {
        case x if x <= 3 => new DeclaredDependency(new UnifiedDependency(null, null, null, null), dataContext)
        case _ => new DeclaredDependency(new UnifiedDependency(libs(0), libs(1), libs(2), libDependencyData.getScope.toString), dataContext)
      }
    }).toList.asJava
  }

  override def declaredRepositories(module: OpenapiModule.Module): util.List[UnifiedDependencyRepository] = {
    List().asJava
  }
}
