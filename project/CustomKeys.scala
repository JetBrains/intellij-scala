import sbt._

import Packaging.PackageEntry

object CustomKeys {
  lazy val sdkDirectory = settingKey[File]("Path to SDK directory where unmanagedJars and IDEA are located")
  lazy val packageStructure = settingKey[Seq[PackageEntry]]("Structure of plugin package")
  lazy val packagePlugin = taskKey[File]("Package scala plugin locally")
  lazy val packagePluginZip = taskKey[File]("Package and compress scala plugin locally")
}
