package org.jetbrains.sbt.project.template.wizard

import org.jetbrains.plugins.scala.project.Versions

private[template]
final case class SbtModuleStepSelections(
  var sbtVersion: Option[String],
  var scalaVersion: Option[String],
  var resolveClassifiers: Boolean,
  var resolveSbtClassifiers: Boolean,
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

object SbtModuleStepSelections {

  def default: SbtModuleStepSelections =
    SbtModuleStepSelections(
      sbtVersion = scala.Option.empty,
      scalaVersion = scala.Option.empty,
      resolveClassifiers = true,
      resolveSbtClassifiers = false,
      packagePrefix = scala.Option.empty
    )
}