package org.jetbrains.bsp.project

import com.intellij.openapi.module.Module
import org.jetbrains.bsp.BspUtil
import org.jetbrains.sbt.project.BuildToolModuleHandler

//noinspection ApiStatus
private final class BspModuleHandler extends BuildToolModuleHandler {
  override def handles(module: Module): Boolean = BspUtil.isBspModule(module)
}
