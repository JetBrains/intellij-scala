name := "intellij-scalastyle"

organization := "JetBrains"

scalaVersion := "2.11.2"

unmanagedJars in Compile ++= (baseDirectory.value / "jars" * "*.jar").classpath

unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"
