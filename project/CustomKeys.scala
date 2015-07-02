import sbt._

import Packaging.PackageEntry

object CustomKeys {
  lazy val sdkDirectory = SettingKey[File]("sdk-directory", "Path to SDK directory where unmanagedJars and IDEA are located")
  lazy val packageStructure = SettingKey[Seq[PackageEntry]]("package-structure", "Structure of plugin's package")
  lazy val packagePlugin = TaskKey[File]("package-plugin", "Package scala plugin locally")
  lazy val packagePluginZip = TaskKey[File]("package-plugin-zip", "Package and compress scala plugin locally")
}
