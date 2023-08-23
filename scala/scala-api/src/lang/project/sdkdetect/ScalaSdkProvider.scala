package org.jetbrains.plugins.scala.project.sdkdetect

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.project.sdkdetect.repository.ScalaSdkDetector
import org.jetbrains.plugins.scala.project.template.SdkChoice

import java.util.function.Consumer

final class ScalaSdkProvider(
  indicator: ProgressIndicator,
  scalaJarDetectors: Seq[ScalaSdkDetector]
) {

  def discoverSDKs(callback: Consumer[SdkChoice], onFinish: => Unit): Unit = {
    // TODO: coursier SDKs are shown with a big delay because coursier needs to scan more folders
    //  we could show the progress "Searching for SDKs in coursier" in the dialog itself
    scalaJarDetectors.foreach { detector: ScalaSdkDetector =>
      indicator.checkCanceled()
      indicator.setText(message("sdk.scan.title", detector.friendlyName))
      indicator.setIndeterminate(true)

      val sdkChoices = detector.collectSdkChoices(indicator)
      val sdkChaisesSorted = sdkChoices.sortBy(_.sdk.version).reverse
      sdkChaisesSorted.foreach(callback.accept)
    }
    onFinish
  }
}