package org.jetbrains.bsp.project

import com.intellij.openapi.module.Module
import org.jetbrains.sbt.project.SbtVersionProvider

final class BspSbtVersionProvider extends SbtVersionProvider {
  override def getSbtVersion(module: Module): Option[String] = {
    val sbtBuildModuleData = BspExternalSystemUtil.getSbtBuildModuleDataBsp(module)
    sbtBuildModuleData.map(_.sbtVersion)
  }
}
