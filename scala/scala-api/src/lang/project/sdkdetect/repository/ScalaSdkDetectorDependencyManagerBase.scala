package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.project.template.{ScalaSdkComponent, ScalaSdkDescriptor}

import java.nio.file.Path
import java.util.stream.{Stream => JStream}
import scala.collection.immutable.ArraySeq

/**
 * Represents detectors which can't locate several Scala SDKs with the same exact version.
 * Each scala artifact version is represented by a single jar file.
 * This can be not the case for example for SystemDetector which can contain multiple folders with same scala version
 * (see SCL-19219)
 */
abstract class ScalaSdkDetectorDependencyManagerBase extends ScalaSdkDetectorBase {

  /**
   * Returns sequence of streams with a potential label
   * For most of the detectors the label is empty, for System detector it denotes the folder name (see SCL-19219)
   */
  protected def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path]

  protected def collectSdkDescriptors(implicit indicator: ProgressIndicator): Seq[ScalaSdkDescriptor] = {
    val jarStream = buildJarStream(indicator)
    val components: Seq[ScalaSdkComponent] = componentsFromJarStream(jarStream)

    val componentsByVersion: Seq[(Option[String], Seq[ScalaSdkComponent])] =
      components.groupBy(_.version).to(ArraySeq)

    val sdkDescriptors: Seq[(Option[String], Either[Seq[String], ScalaSdkDescriptor])] =
      componentsByVersion.map { case (version, components) =>
        val descriptor = buildFromComponents(components, None, indicator)
        (version, descriptor.left.map(_.map(_.errorMessage)))
      }

    sdkDescriptors.flatMap {
      case (Some(_), Right(descriptor)) => Some(descriptor)
      case (version, Left(errors))      => logScalaSdkSkipped(version, errors); None
      case (None, Right(descriptor))    => logScalaSdkSkipped_UndefinedVersion(descriptor); None
    }
  }
}





