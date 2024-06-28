package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.module.Module

import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.sbt.project.data.service.SbtSourceSetDataService.sbtSourceSetModuleType
import org.jetbrains.sbt.project.module.SbtSourceSetData

class SbtSourceSetDataService extends AbstractSbtModuleDataService[SbtSourceSetData] {

  override def getTargetDataKey: Key[SbtSourceSetData] = SbtSourceSetData.Key

  override protected def moduleType: String = sbtSourceSetModuleType

  override def setModuleOptions(module: Module, moduleDataNode: DataNode[SbtSourceSetData]): Unit = {
    super.setModuleOptions(module, moduleDataNode)
    ExternalSystemModulePropertyManager.getInstance(module).setExternalModuleType(sbtSourceSetModuleType)
  }

  override protected def generateNewName(
    parentModule: Module,
    data: SbtSourceSetData,
    parentModuleActualName: String
  ): Option[String] =
    Some(s"$parentModuleActualName.${data.getModuleName}")
}

object SbtSourceSetDataService {
  @VisibleForTesting
  val sbtSourceSetModuleType = "sbtSourceSet"
}
