package org.jetbrains.plugins.scala.project.sdkdetect

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.project.sdkdetect.ScalaSdkProvider.Log
import org.jetbrains.plugins.scala.project.sdkdetect.repository.ScalaSdkDetector
import org.jetbrains.plugins.scala.project.template.{ScalaSdkComponent, ScalaSdkDescriptor, SdkChoice}

import java.util.function.Consumer
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

// TODO: use Java -> Scala stream converters from Scala 2.13
final class ScalaSdkProvider(
  indicator: ProgressIndicator,
  scalaJarDetectors: Seq[ScalaSdkDetector]
) {

  def discoverSDKs(callback: Consumer[SdkChoice]): Unit = {
    // TODO: coursier SDKs are shown with a big delay because coursier needs to scan more folders
    //  we could show the progress "Searching for SDKs in coursier" in the dialog itself
    scalaJarDetectors.foreach { detector =>
      indicator.setText(message("sdk.scan.title", detector.friendlyName))
      indicator.setIndeterminate(true)

      val jarStream = detector.buildJarStream(indicator)
      val components: Seq[ScalaSdkComponent] = try {
        jarStream
          .iterator().asScala
          .map { f =>
            indicator.checkCanceled()
            //noinspection ReferencePassedToNls
            indicator.setText2(f.toString)
            val sdkComponent = ScalaSdkComponent.fromFile(f.toFile)
            sdkComponent.orNull
          }
          .filter(_ != null)
          .toSeq
      } finally {
        jarStream.close()
      }

      val componentsByVersion: Seq[(Option[String], Seq[ScalaSdkComponent])] =
        components.groupBy(_.version).to(ArraySeq)

      val sdkDescriptors: Seq[(Option[String], Either[Seq[String], ScalaSdkDescriptor])] =
        componentsByVersion.map { case (version, components) =>
          val descriptor = ScalaSdkDescriptor.buildFromComponentsFull(detector, components, indicator)
          (version, descriptor.left.map(_.map(_.errorMessage)))
        }

      sdkDescriptors.foreach {
        case (version, Left(errors)) =>
          Log.trace(
            s"Scala SDK Descriptor candidate is skipped" +
              s" (detector: ${detector.getClass.getSimpleName}, scalaVersion: $version)," +
              s" errors: ${errors.zipWithIndex.map(_.swap).mkString(", ")}"
          )
        case _ =>
      }

      val sdkChaisesSorted = sdkDescriptors
        .collect { case (Some(_), Right(descriptor)) => detector.buildSdkChoice(descriptor) }
        .sortBy(_.sdk.version)
        .reverse

      sdkChaisesSorted.foreach(callback.accept)
    }
  }
}

object ScalaSdkProvider {

  private val Log = Logger.getInstance(classOf[ScalaSdkProvider])
}
