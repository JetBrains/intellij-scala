package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.project.template._
import org.jetbrains.sbt.RichBoolean

trait ScalaSdkDetector {

  @Nls
  def friendlyName: String

  def collectSdkChoices(implicit indicator: ProgressIndicator): Seq[SdkChoice]
}

object ScalaSdkDetector {
  def allDetectors(contextDirectory: VirtualFile): Seq[ScalaSdkDetectorBase] =
    Seq(
      new ProjectLocalDetector(contextDirectory),
      SystemDetector,
      BrewDetector,
      IvyDetector
    ) ++
    isIdeaPluginEnabled("org.jetbrains.idea.maven").seq(
      MavenDetector
    ) ++
    Seq(
      SdkmanDetector,
      CoursierDetector
    )

  private def isIdeaPluginEnabled(id: String): Boolean =
    Option(PluginId.findId(id)).map(PluginManagerCore.getPlugin).exists(_.isEnabled)
}
