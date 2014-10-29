name := "scala-jps-plugin"

organization := "JetBrains"

scalaVersion := "2.11.2"

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/sbt" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"

unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"
