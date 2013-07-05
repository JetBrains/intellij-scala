name := "intellij-sbt"

organization := "org.jetbrains"

version := "0.1"

scalaVersion := "2.10.2"

unmanagedJars in Compile <++= ideaDirectory.map(base => ((base / "lib") ** "*.jar").classpath)

unmanagedJars in Compile <++= scalaPluginDirectory.map(base => ((base / "classes" / "artifacts" / "Scala" / "lib") ** "*.jar").classpath)
