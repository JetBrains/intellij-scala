package org.jetbrains.plugins.scala.project.sdkdetect


import java.util.stream.Collectors

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.project.template.{ScalaSdkComponent, ScalaSdkDescriptor, SdkChoice}

import scala.collection.JavaConverters._

// TODO: use Java -> Scala stream converters from Scala 2.13
class ScalaSdkProvider(indicator: ProgressIndicator, contextDirectory: VirtualFile) {

  protected val scalaJarDetectors: Seq[ScalaSdkDetector] =
    Seq(new ProjectLocalDetector(contextDirectory), SystemDetector, BrewDetector, IvyDetector, MavenDetector)

  def discoverSDKsAsync(callback: SdkDiscoveredCallback = _ => ()): Seq[SdkChoice] = scalaJarDetectors.flatMap { detector =>
    indicator.setText(message("sdk.scan.title", detector.friendlyName))
    indicator.setIndeterminate(true)

    val components = detector.buildJarStream
      .map[ScalaSdkComponent] { f =>
        indicator.checkCanceled()
        indicator.setText2(f.toString)
        ScalaSdkComponent.fromFile(f.toFile).orNull
      }
      .filter(_ != null)
      .collect(Collectors.toList[ScalaSdkComponent]).asScala

    val componentsByVersion = components.groupBy(_.version)
    componentsByVersion
      .mapValues(ScalaSdkDescriptor.buildFromComponents)
      .toSeq
      .collect {
        case (Some(_), Right(descriptor)) =>
          val choice = detector.buildSdkChoice(descriptor)
          callback.sdkDiscovered(choice)
          choice
      }
  }

}
