package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.util.SbtModuleType.sbtSourceSetModuleType
import org.jetbrains.sbt.project.module.SbtSourceSetData

class SbtSourceSetDataService extends AbstractSbtModuleDataService[SbtSourceSetData] {

  override def getTargetDataKey: Key[SbtSourceSetData] = SbtSourceSetData.Key

  override protected def moduleType: String =
    sbtSourceSetModuleType

  override protected def generateNewName(
    parentModule: Module,
    data: SbtSourceSetData,
    parentModuleActualName: String
  ): Option[String] =
    Some(s"$parentModuleActualName.${data.getModuleName}")
}
