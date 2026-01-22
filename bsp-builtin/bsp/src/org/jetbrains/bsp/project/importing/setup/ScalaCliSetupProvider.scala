package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

import java.nio.file.Path
import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * Extension point for providing Scala CLI configuration setup.
 * This allows the scala-cli module to register its config setup implementation without requiring the bsp module to depend on it.
 */
@ApiStatus.Internal
trait ScalaCliSetupProvider {
  /** Checks if this provider can import the Scala CLI project in the given workspace. */
  def canImport(workspace: Path): Boolean

  def getBspConfigSetup(workspace: Path): BspConfigSetup
}

private[bsp] object ScalaCliSetupProvider {

  private val EP = ExtensionPointName.create[ScalaCliSetupProvider]("org.intellij.bsp.scalaCliSetupProvider")

  /** Checks if any [[ScalaCliSetupProvider]] provider can import the Scala CLI project in the given workspace. */
  def canImport(workspace: Path): Boolean =
    getImplementations.exists(_.canImport(workspace))

  /** Get the [[BspConfigSetup]] for Scala CLI build tool. */
  def getBspConfigSetup(workspace: Path): Option[BspConfigSetup] =
    getImplementations.find(_.canImport(workspace)).map(_.getBspConfigSetup(workspace))

  private def getImplementations: Option[ScalaCliSetupProvider] =
    EP.getExtensionList.iterator().asScala.nextOption()
}
