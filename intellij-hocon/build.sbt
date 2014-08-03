name := "intellij-hocon"

organization := "JetBrains"

scalaVersion := "2.11.2"

val ideaBasePath = "SDK/ideaSDK/idea13"

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / ideaBasePath / "lib" * "*.jar").classpath