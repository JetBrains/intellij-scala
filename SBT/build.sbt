name := "SBT"

organization := "JetBrains"

scalaVersion := "2.11.2"

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK" / "maven-indexer" * "*.jar").classpath