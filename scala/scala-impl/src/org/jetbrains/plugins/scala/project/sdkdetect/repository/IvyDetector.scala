package org.jetbrains.plugins.scala.project.sdkdetect.repository

import java.nio.file.{Path, Paths}
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.project.template.{PathExt, _}

private[repository] object IvyDetector extends ScalaSdkDetector {
  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = IvySdkChoice(descriptor)
  override def friendlyName: String = "Ivy2 cache"

  override def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val homePrefix = Paths.get(sys.props("user.home"))
    val ivyHome    = sys.props.get("sbt.ivy.home").map(Paths.get(_)).orElse(Option(homePrefix / ".ivy2")).get
    val scalaRoot = ivyHome / "cache" / "org.scala-lang"

    if (scalaRoot.exists)
      collectJarFiles(scalaRoot)
    else
      JStream.empty()
  }
}