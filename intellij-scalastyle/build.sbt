name := "intellij-scalastyle"

organization := "JetBrains"

scalaVersion := "2.11.2"

val ideaBasePath = "SDK/ideaSDK/idea13"

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / ideaBasePath / "lib" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value / "jars" * "*.jar").classpath