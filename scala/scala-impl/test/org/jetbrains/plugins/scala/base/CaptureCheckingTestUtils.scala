package org.jetbrains.plugins.scala.base

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.{ScalaFeatures, ScalaModuleSettings}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.RevertableChange

private[scala] object CaptureCheckingTestUtils {
  /** Features for syntax-only fixtures that do not configure a module or Scala SDK. */
  val parsingFeatures: ScalaFeatures.SerializableScalaFeatures =
    ScalaFeatures.custom(ScalaVersion.Latest.Scala_3_8, hasCaptureCheckingEnabled = true)

  /** Options for Scala SDK-backed fixtures' `additionalCompilerOptions` hook. */
  val compilerOptions: Seq[String] = Seq("-language:experimental.captureChecking")

  /**
   * Creates a reversible change that enables capture checking in the default compiler profile.
   *
   * The enabled settings are based on the profile state when this method is called.
   * Applying the change records the settings to restore; `run` restores them after its body,
   * including when the body fails.
   *
   * @param project project whose default compiler profile is modified
   * @return an unapplied settings change for a block or fixture lifetime
   */
  def captureCheckingCompilerSettingsChange(project: Project): RevertableChange = {
    val profile = ScalaCompilerConfiguration.instanceIn(project).defaultProfile
    val settings = profile.getSettings

    RevertableChange.withModifiedSetting(
      getter = profile.getSettings,
      setter = updatedSettings => profile.setSettings(updatedSettings),
      newValue = settings.copy(
        additionalCompilerOptions = settings.additionalCompilerOptions ++ compilerOptions
      )
    )
  }

  /**
   * Enables capture checking in a light module without installing a Scala SDK.
   * Sets the module's Scala-version override and compiler option so files in its
   * source roots retain capture syntax during committed document reparses.
   *
   * @param parentDisposable restores the compiler profile and clears the version
   *                         override when the test ends
   */
  def enableCaptureCheckingInLightModule(
    module: Module,
    scalaVersion: ScalaVersion,
    parentDisposable: Disposable
  ): Unit = {
    val compilerSettingsChange = captureCheckingCompilerSettingsChange(module.getProject)

    ScalaModuleSettings.TestUtils.setModuleScalaVersionForLightTests(module, scalaVersion)
    compilerSettingsChange.applyChange()

    Disposer.register(parentDisposable, () => {
      compilerSettingsChange.revertChange()
      ScalaModuleSettings.TestUtils.setModuleScalaVersionForLightTests(module, null)
    })
  }
}
