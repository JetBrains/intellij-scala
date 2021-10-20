package org.jetbrains.sbt.project.template

import org.jetbrains.plugins.scala.project.Versions

final case class SbtModuleBuilderSelections(
  var sbtVersion: Option[String],
  var scalaVersion: Option[String],
  var downloadScalaSdkSources: Boolean,
  var downloadSbtSources: Boolean,
  var packagePrefix: Option[String]
) {

  /**
   * For now we show latest Scala 2 version in the dropdown list.<br>
   * If the user wants to select some other version we need to show that there are Scala 3 versions above the the selected version.<br>
   * By default combo box will show the selected element at the top and it's not clear that there are other versions above it.
   *
   * @see [[org.jetbrains.plugins.scala.project.Versions.Scala.initiallySelectedVersion]]
   */
  var scrollScalaVersionDropdownToTheTop = false

  def versionFromKind(kind: Versions.Kind): Option[String] = kind match {
    case Versions.Scala => scalaVersion
    case Versions.SBT   => sbtVersion
  }

  def update(kind: Versions.Kind, versions: Versions): Unit = {
    val explicitlySelectedVersion = versionFromKind(kind)
    val version = explicitlySelectedVersion.getOrElse(kind.initiallySelectedVersion(versions.versions))

    kind match {
      case Versions.Scala =>
        scalaVersion = Some(version)
        scrollScalaVersionDropdownToTheTop = explicitlySelectedVersion.isEmpty
      case Versions.SBT   =>
        sbtVersion = Some(version)
    }
  }
}

object SbtModuleBuilderSelections {

  def default: SbtModuleBuilderSelections =
    SbtModuleBuilderSelections(
      sbtVersion = scala.Option.empty,
      scalaVersion = scala.Option.empty,
      downloadScalaSdkSources = true,
      downloadSbtSources = false,
      packagePrefix = scala.Option.empty
    )
}