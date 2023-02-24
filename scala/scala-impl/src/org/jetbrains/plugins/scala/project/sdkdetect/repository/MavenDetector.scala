package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.template.{PathExt, _}

import java.nio.file.Path
import java.util.stream.{Stream => JStream}

private[repository] object MavenDetector extends ScalaSdkDetectorDependencyManagerBase {

  override def friendlyName: String = ScalaBundle.message("maven.local.repo")

  override protected def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = MavenSdkChoice(descriptor)

  override protected def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val mavenHomeDir = Option(Path.of(SystemProperties.getUserHome, ".m2"))
    val scalaRoot = mavenHomeDir.map(_ / "repository" / "org" / "scala-lang")
    scalaRoot.filter(_.exists).map(collectJarFiles).getOrElse(JStream.empty())
  }
}