package org.jetbrains.plugins.scala
package project
package template

/**
 * @author Pavel Fatin
 */
sealed abstract class SdkChoice(val sdk: ScalaSdkDescriptor,
                                val source: String)

case class ProjectSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Project")
case class SystemSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "System")
case class IvySdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Ivy")
case class MavenSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Maven")
case class CoursierSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Coursier")
case class BrewSdkChoice(override val sdk: ScalaSdkDescriptor) extends SdkChoice(sdk, "Brew")