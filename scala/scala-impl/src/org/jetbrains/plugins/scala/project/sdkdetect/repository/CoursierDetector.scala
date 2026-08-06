package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.progress.ProgressIndicator
import coursier.paths.CoursierPaths
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt}
import org.jetbrains.plugins.scala.project.template.{CoursierSdkChoice, ScalaSdkDescriptor, SdkChoice}

import java.nio.file.Path
import java.util.function.{Function => JFunction}
import java.util.stream.{Stream => JStream}

private[repository] object CoursierDetector extends ScalaSdkDetectorDependencyManagerBase {

  override def friendlyName: String = ScalaBundle.message("coursier.v1.cache")

  override protected def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = CoursierSdkChoice(descriptor)

  override protected def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val cacheRoot = getCoursierCacheV1.filter(_.exists)

    val maybeStream = cacheRoot.map { v1 =>
      val scalaLangArtifactsDir = v1
        .walk()
        .filter { f => progress(f.toString); f.isDirectory && f.nameContains("scala-lang") }

      scalaLangArtifactsDir
        .map[JStream[Path]](collectJarFiles)
        .flatMap(JFunction.identity[JStream[Path]]())
    }

    maybeStream.getOrElse(JStream.empty[Path]())
  }

  private def getCoursierCacheV1: Option[Path] = CoursierPaths.cacheDirectoryPath().toOption
}
