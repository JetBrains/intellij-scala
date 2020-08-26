package org.jetbrains.plugins.scala.project.sdkdetect

import java.util.function.Consumer
import java.util.stream.Collectors

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.project.template.{ScalaSdkComponent, ScalaSdkDescriptor, SdkChoice}
import org.jetbrains.plugins.scala.project.sdkdetect.repository.ScalaSdkDetector

import scala.jdk.CollectionConverters._

// TODO: use Java -> Scala stream converters from Scala 2.13
class ScalaSdkProvider(implicit indicator: ProgressIndicator, contextDirectory: VirtualFile) {

  protected val scalaJarDetectors: Seq[ScalaSdkDetector] = ScalaSdkDetector.allDetectors(contextDirectory)

  def discoverSDKs(callback: Consumer[SdkChoice]): Seq[SdkChoice] = scalaJarDetectors.flatMap(detector => {
    indicator.setText(message("sdk.scan.title", detector.friendlyName))
    indicator.setIndeterminate(true)

    val jarStream = detector.buildJarStream
    val components = try {
      jarStream
        .map[ScalaSdkComponent] { f =>
          indicator.checkCanceled()
          //noinspection ReferencePassedToNls
          indicator.setText2(f.toString)
          ScalaSdkComponent.fromFile(f.toFile).orNull
        }
        .filter(_ != null)
        .collect(Collectors.toList[ScalaSdkComponent]).asScala
    } finally {
      jarStream.close()
    }

    val componentsByVersion = components.groupBy(_.version)
    componentsByVersion
      .view
      .mapValues(ScalaSdkDescriptor.buildFromComponents)
      .toSeq
      .collect { case (Some(_), Right(descriptor)) => detector.buildSdkChoice(descriptor) }
      .sortBy(_.sdk.version)
      .reverse
      .map { sdk => callback.accept(sdk); sdk}
  })

}
