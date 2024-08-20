package org.jetbrains.sbt.project.template

import org.jetbrains.plugins.scala.project.Versions

class ScalaModuleBuilderSelections(
  var scalaVersion: Option[String],
  var downloadScalaSdkSources: Boolean
) {

  /**
   * For now we show latest Scala 2 version in the dropdown list.<br>
   * If the user wants to select some other version we need to show that there are Scala 3 versions above the the selected version.<br>
   * By default combo box will show the selected element at the top and it's not clear that there are other versions above it.
   *
   * @see [[org.jetbrains.plugins.scala.project.Versions.Scala.initiallySelectedVersion]]
   */
  var scrollScalaVersionDropdownToTheTop = false

  def updateScalaVersion(versions: Versions): Unit = {
    val version = scalaVersion.getOrElse(Versions.Scala.initiallySelectedVersion(versions.versions))

    scalaVersion = Some(version)
    scrollScalaVersionDropdownToTheTop = scalaVersion.isEmpty
  }

  def copy(): ScalaModuleBuilderSelections =
    new ScalaModuleBuilderSelections(scalaVersion, downloadScalaSdkSources)
}

final class SbtModuleBuilderSelections(
  var sbtVersion: Option[String],
  _scalaVersion: Option[String],
  _downloadScalaSdkSources: Boolean,
  var downloadSbtSources: Boolean,
  var packagePrefix: Option[String]
) extends ScalaModuleBuilderSelections(_scalaVersion, _downloadScalaSdkSources){

  override def copy(): SbtModuleBuilderSelections =
    new SbtModuleBuilderSelections(sbtVersion, scalaVersion, downloadScalaSdkSources, downloadSbtSources, packagePrefix)

  def updateSbtVersion(versions: Versions): Unit = {
    val version = sbtVersion.getOrElse(Versions.SBT.initiallySelectedVersion(versions.versions))
    sbtVersion = Some(version)
  }
}

object SbtModuleBuilderSelections {

  def default: SbtModuleBuilderSelections =
    new SbtModuleBuilderSelections(
      sbtVersion = scala.Option.empty,
      _scalaVersion = scala.Option.empty,
      _downloadScalaSdkSources = true,
      downloadSbtSources = false,
      packagePrefix = scala.Option.empty
    )
}

object ScalaModuleBuilderSelections {

  def default: ScalaModuleBuilderSelections =
    new ScalaModuleBuilderSelections(
      scalaVersion = scala.Option.empty,
      downloadScalaSdkSources = true
    )
}