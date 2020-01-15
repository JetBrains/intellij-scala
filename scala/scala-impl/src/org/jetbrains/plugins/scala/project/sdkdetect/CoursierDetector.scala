package org.jetbrains.plugins.scala.project.sdkdetect
import java.nio.file._
import java.util.function.{Function => JFunction}
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.project.template._

object CoursierDetector extends ScalaSdkDetector {
  val COURSIER_CACHE_ENV = "COURSIER_CACHE"

  def getCoursierCacheV1: Option[Path] =
    if (SystemInfo.isWindows)
      sys.env.get("LOCALAPPDATA").map(Paths.get(_) / "Coursier" / "Cache" / "v1")
    else if (SystemInfo.isMac)
      sys.props.get("user.home").map(Paths.get(_) / "Library" / "Caches" / "Coursier" / "v1")
    else if (SystemInfo.isLinux)
      sys.props.get("user.home").map(Paths.get(_) / ".coursier" / "cache" / "v1")
    else None

  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = CoursierSdkChoice(descriptor)
  override def friendlyName: String = "Coursier v1 cache"

  override def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] =
    getCoursierCacheV1.filter(_.exists).map { v1 =>
      v1
        .walk
        .filter { f => progress(f.toString); f.isDir && f.getFileName.nameContains("scala-lang") }
        .map[JStream[Path]](collectJarFiles)
        .flatMap(JFunction.identity[JStream[Path]]())
    }.getOrElse(JStream.empty[Path]())
}
