package org.jetbrains.plugins.scala.project.template

import org.jetbrains.plugins.scala.project.Version

import java.io.File

/**
 * @param label extra label which allows to distinguish between several SDKs with the same Scala version.
 *              For SystemDetector we can have several folders with Scala SDK with same version (see SCL-19219)
 *              For other detectors (Ivy, Maven, etc...) this value will be None
 */
final case class ScalaSdkDescriptor(version: Option[String], // Why is it Option? Shouldn't SDK version always be known?
                                    label: Option[String],
                                    compilerClasspath: Seq[File],
                                    scaladocExtraClasspath: Seq[File],
                                    libraryFiles: Seq[File],
                                    sourceFiles: Seq[File],
                                    docFiles: Seq[File])
  extends Ordered[ScalaSdkDescriptor] {

  def isScala3: Boolean = version.exists(_.startsWith("3"))

  def withExtraCompilerClasspath(files: Seq[File]): ScalaSdkDescriptor = copy(compilerClasspath = compilerClasspath ++ files)
  def withExtraLibraryFiles(files: Seq[File]): ScalaSdkDescriptor = copy(libraryFiles = libraryFiles ++ files)
  def withExtraSourcesFiles(files: Seq[File]): ScalaSdkDescriptor = copy(sourceFiles = sourceFiles ++ files)
  def withLabel(label: Option[String]): ScalaSdkDescriptor = copy(label = label)

  private val comparableVersion = version.map(Version(_))

  override def compare(that: ScalaSdkDescriptor): Int = that.comparableVersion.compare(comparableVersion)
}