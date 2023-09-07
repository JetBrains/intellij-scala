package org.jetbrains.plugins.scalaDirective.dependencies

import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.{ArtifactIdSuffix, DependencyDescriptor}

object ScalaDirectiveDependencyDescriptor {
  def unapply(dependencyText: String): Option[DependencyDescriptor] = {
    val tokens = dependencyText.split(":", -1).map(s => Option.when(s.nonEmpty)(s))
    tokens match {
      // org:artifact:[version]
      case Array(Some(groupId), Some(artifactId), version) =>
        Some(DependencyDescriptor(groupId, artifactId, version))
      // org::artifact:[version]
      case Array(Some(groupId), None, Some(artifactId), version) =>
        Some(DependencyDescriptor(groupId, artifactId, version, ArtifactIdSuffix.ScalaVersion))
      // org::artifact::[version]
      case Array(Some(groupId), None, Some(artifactId), None, version) =>
        Some(DependencyDescriptor(groupId, artifactId, version, ArtifactIdSuffix.ScalaVersion, platform = true))
      // org:::artifact:[version]
      case Array(Some(groupId), None, None, Some(artifactId), version) =>
        Some(DependencyDescriptor(groupId, artifactId, version, ArtifactIdSuffix.FullScalaVersion))
      // org:::artifact::[version]
      case Array(Some(groupId), None, None, Some(artifactId), None, version) =>
        Some(DependencyDescriptor(groupId, artifactId, version, ArtifactIdSuffix.FullScalaVersion, platform = true))
      case _ => None
    }
  }

  /** Convert dependency descriptor to directive dependency value presentation */
  def render(descriptor: DependencyDescriptor): String = {
    val groupArtifactSeparator = descriptor.artifactIdSuffix match {
      case ArtifactIdSuffix.Empty => ":"
      case ArtifactIdSuffix.ScalaVersion => "::"
      case ArtifactIdSuffix.FullScalaVersion => ":::"
    }
    val artifactVersionSeparator = if (descriptor.platform) "::" else ":"
    s"${descriptor.groupId}$groupArtifactSeparator${descriptor.artifactId}$artifactVersionSeparator${descriptor.version.getOrElse("")}"
  }
}
