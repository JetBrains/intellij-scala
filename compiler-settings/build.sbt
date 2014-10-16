name := "compiler-settings"

organization := "JetBrains"

scalaVersion := "2.11.2"

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"
