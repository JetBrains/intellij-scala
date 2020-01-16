package org.jetbrains.plugins.scala.project.sdkdetect

import java.nio.file.{Files, Path}
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.project.template._

trait ScalaSdkDetector {
  def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path]
  def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice
  def friendlyName: String

  protected def collectJarFiles(path: Path)(implicit indicator: ProgressIndicator): JStream[Path] =
    Files.walk(path).filter { indicator.checkCanceled(); _.toString.endsWith(".jar") }

  protected def progress(text2: String)(implicit indicator: ProgressIndicator): Unit = {
    indicator.checkCanceled()
    indicator.setText2(text2)
  }
}







