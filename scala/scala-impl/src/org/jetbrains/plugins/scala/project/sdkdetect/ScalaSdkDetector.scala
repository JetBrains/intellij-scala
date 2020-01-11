package org.jetbrains.plugins.scala.project.sdkdetect

import java.nio.file.{Files, Path}
import java.util.stream.{Stream => JStream}

import org.jetbrains.plugins.scala.project.template._

trait ScalaSdkDetector {
  def buildJarStream: JStream[Path]
  def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice
  def friendlyName: String

  protected def collectJarFiles(path: Path): JStream[Path] = Files.walk(path).filter(_.toString.endsWith(".jar"))
}







