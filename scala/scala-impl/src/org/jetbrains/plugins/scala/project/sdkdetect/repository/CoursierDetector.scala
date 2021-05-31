package org.jetbrains.plugins.scala.project.sdkdetect.repository
import java.nio.file._
import java.util.function.{Function => JFunction}
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.template._

private[repository] object CoursierDetector extends ScalaSdkDetector {
  val COURSIER_CACHE_ENV = "COURSIER_CACHE"

  def getCoursierCacheV1: Option[Path] = CoursierPaths.cacheDirectory().toOption.map(_.toPath)

  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = CoursierSdkChoice(descriptor)
  override def friendlyName: String = ScalaBundle.message("coursier.v1.cache")

  override def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val cacheRoot = getCoursierCacheV1.filter(_.exists)

    val maybeStream = cacheRoot.map { v1 =>
      val scalaLangArtifactsDir = v1
        .walk
        .filter { f => progress(f.toString); f.isDir && f.getFileName.nameContains("scala-lang") }

      scalaLangArtifactsDir
        .map[JStream[Path]](collectJarFiles)
        .flatMap(JFunction.identity[JStream[Path]]())
    }

    maybeStream.getOrElse(JStream.empty[Path]())
  }
}
