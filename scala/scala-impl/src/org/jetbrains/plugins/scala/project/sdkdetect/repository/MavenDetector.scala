package org.jetbrains.plugins.scala.project.sdkdetect.repository

import java.nio.file.Path
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.template.{PathExt, _}


private[repository] object MavenDetector extends ScalaSdkDetector {
  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = MavenSdkChoice(descriptor)
  override def friendlyName: String = "Maven local repo"

  override def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val mavenHomeDir = MavenUtil.resolveM2Dir().toOption.map(_.toPath)
    val scalaRoot = mavenHomeDir.map(_ / "repository" / "org" / "scala-lang")
    scalaRoot.filter(_.exists).map(collectJarFiles).getOrElse(JStream.empty())
  }
}