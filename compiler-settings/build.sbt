name := "compiler-settings"

organization := "JetBrains"

scalaVersion := "2.11.2"

lazy val  ideaBasePath = "SDK/ideaSDK/idea-"   + readIdeaPropery("ideaVersion"  )

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / ideaBasePath / "lib" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"
