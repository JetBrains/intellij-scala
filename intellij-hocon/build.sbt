name := "intellij-hocon"

organization := "JetBrains"

scalaVersion := "2.11.2"

unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"

baseDirectory in Test := baseDirectory.value.getParentFile

fork := true

