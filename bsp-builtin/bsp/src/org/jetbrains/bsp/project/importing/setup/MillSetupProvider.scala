package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.EelProviderUtil
import org.jetbrains.bsp.project.importing.bspConfigSteps
import org.jetbrains.bsp.project.importing.bspConfigSteps.ConfigSetup

import java.nio.file.Path

/** BSP setup provider for Mill build tool. */
class MillSetupProvider extends BspSetupProvider {

  override def configSetup: ConfigSetup =
    bspConfigSteps.MillSetup

  override def canImport(workspace: Path): Boolean =
    MillConfigSetup.canImport(workspace)

  override def getBspConfigSetup(workspace: Path): BspConfigSetup =
    new MillConfigSetup(workspace)

  override def bspBuildFileNames(project: Project): Seq[String] =
    MillConfigSetup.buildFileNames(EelProviderUtil.getEelDescriptor(project))
}
