package org.jetbrains.plugins.scala.worksheet.bsp

import com.intellij.openapi.module.Module
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.test.environment.BspJvmEnvironment
import org.jetbrains.plugins.scala.worksheet.WorksheetCompilerExtension
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentAction

import java.io.File

private final class BspWorksheetCompilerExtension extends WorksheetCompilerExtension {
  override def worksheetClasspath(module: Module): Option[Seq[File]] = {
    if (BspUtil.isBspModule(module)) {
      BspJvmEnvironment.resolveForWorksheet(module).toOption.map { env =>
        env.classpath.map(_.toFile)
      }
    } else None
  }

  override def extraWorksheetActions(): Seq[TopComponentAction] = {
    Seq(new ConfigureBspTargetForWorksheet)
  }
}
