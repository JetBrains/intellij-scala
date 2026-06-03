package org.jetbrains.plugins.scala.project.template

import org.jetbrains.plugins.scala.project.{ReplClasspath, Version}

import java.nio.file.Path

/**
 * @param label extra label which allows to distinguish between several SDKs with the same Scala version.
 *              For SystemDetector we can have several folders with Scala SDK with same version (see SCL-19219)
 *              For other detectors (Ivy, Maven, etc...) this value will be None
 * @param systemRoot location of the scala sdk standalone distribution if the SDK was detected from such
 */
final case class ScalaSdkDescriptor(version: Option[String], // Why is it Option? Shouldn't SDK version always be known?
                                    label: Option[String],
                                    compilerClasspath: Seq[Path],
                                    scaladocExtraClasspath: Seq[Path],
                                    libraryFiles: Seq[Path],
                                    sourceFiles: Seq[Path],
                                    docFiles: Seq[Path],
                                    compilerBridgeJar: Option[Path],
                                    replClasspath: Option[ReplClasspath],
                                    systemRoot: Option[Path] = None)
  extends Ordered[ScalaSdkDescriptor] {

  def isScala3: Boolean = version.exists(_.startsWith("3"))

  def withExtraCompilerClasspath(files: Seq[Path]): ScalaSdkDescriptor = copy(compilerClasspath = compilerClasspath ++ files)
  def withExtraLibraryFiles(files: Seq[Path]): ScalaSdkDescriptor = copy(libraryFiles = libraryFiles ++ files)
  def withExtraSourcesFiles(files: Seq[Path]): ScalaSdkDescriptor = copy(sourceFiles = sourceFiles ++ files)
  def withLabel(label: Option[String]): ScalaSdkDescriptor = copy(label = label)

  private val comparableVersion = version.map(Version(_))

  override def compare(that: ScalaSdkDescriptor): Int = that.comparableVersion.compare(comparableVersion)
}
