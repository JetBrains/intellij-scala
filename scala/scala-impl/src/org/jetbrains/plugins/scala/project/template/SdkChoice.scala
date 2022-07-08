package org.jetbrains.plugins.scala
package project
package template

sealed abstract class SdkChoice(val sdk: ScalaSdkDescriptor,
                                val source: String)

final case class ProjectSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Project")
final case class SystemSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "System")
final case class IvySdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Ivy")
final case class MavenSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Maven")
final case class CoursierSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Coursier")
final case class BrewSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Brew")
final case class SdkmanSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "SDKMAN!")