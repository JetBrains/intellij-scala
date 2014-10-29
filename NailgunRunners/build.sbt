name := "NailgunRunners"

organization := "JetBrains"

scalaVersion := "2.11.2"

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath