name := "ScalaRunner"

organization := "JetBrains"

scalaVersion := "2.11.2"

libraryDependencies += "org.specs2" %% "specs2" % "2.3.11" % "provided" excludeAll ExclusionRule(organization = "org.ow2.asm")

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"