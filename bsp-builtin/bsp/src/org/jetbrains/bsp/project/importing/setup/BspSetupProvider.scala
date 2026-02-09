package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.bsp.project.importing.bspConfigSteps.ConfigSetup

import java.nio.file.Path
import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * Extension point for providing BSP configuration setup for various build tools.
 * In practice used internally for Scala CLI and Mill.
 */
@ApiStatus.Internal
trait BspSetupProvider {
  /** Returns the config setup type this provider handles (e.g., MillSetup, ScalaCliSetup). */
  def configSetup: ConfigSetup

  /** Checks if this provider can import the project in the given workspace. */
  def canImport(workspace: Path): Boolean

  def getBspConfigSetup(workspace: Path): BspConfigSetup
}

private[bsp] object BspSetupProvider {

  private val EP = ExtensionPointName.create[BspSetupProvider]("org.intellij.bsp.bspSetupProvider")

  /** Checks if a provider for the specific config setup can import the project in the given workspace. */
  def canImport(workspace: Path, configSetup: ConfigSetup): Boolean =
    getProvider(workspace, configSetup).nonEmpty

  /** Get the [[BspConfigSetup]] for the specific config setup type. */
  def getBspConfigSetup(workspace: Path, configSetup: ConfigSetup): Option[BspConfigSetup] =
    getProvider(workspace, configSetup).map(_.getBspConfigSetup(workspace))

  private def getProvider(workspace: Path, configSetup: ConfigSetup): Option[BspSetupProvider] =
    getImplementations.find(p => p.configSetup == configSetup && p.canImport(workspace))

  private def getImplementations: Seq[BspSetupProvider] =
    EP.getExtensionList.iterator().asScala.toSeq
}
