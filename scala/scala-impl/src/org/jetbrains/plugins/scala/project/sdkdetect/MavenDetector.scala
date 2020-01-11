package org.jetbrains.plugins.scala.project.sdkdetect

import java.nio.file.{Path, Paths}
import java.util.stream.{Stream => JStream}

import org.jetbrains.plugins.scala.project.template.{PathExt, _}


object MavenDetector extends ScalaSdkDetector {
  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = MavenSdkChoice(descriptor)
  override def friendlyName: String = "Maven local repo"

  override def buildJarStream: JStream[Path] = {
    val homePrefix = Paths.get(sys.props("user.home"))
    val scalaRoot = homePrefix / ".m2" / "repository" / "org" / "scala-lang"

    if (scalaRoot.exists)
      collectJarFiles(scalaRoot)
    else
      JStream.empty()
  }
}