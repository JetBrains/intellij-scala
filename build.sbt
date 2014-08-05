name := "ScalaCommunity"

organization := "JetBrains"

scalaVersion := "2.11.2"

libraryDependencies += "org.scalatest" % "scalatest-finders" % "0.9.6"

libraryDependencies += "org.atteo" % "evo-inflector" % "1.2"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.2"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"

unmanagedSourceDirectories in Test += baseDirectory.value / "test"

unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"

val ideaBasePath = "SDK/ideaSDK/idea13"

unmanagedJars in Compile ++= (baseDirectory.value / ideaBasePath / "lib" * "*.jar").classpath

unmanagedJars in Compile ++= {
  val basePluginsDir = baseDirectory.value / ideaBasePath / "plugins"
  val baseDirectories =
    basePluginsDir / "copyright" / "lib" +++
      basePluginsDir / "gradle" / "lib" +++
      basePluginsDir / "Groovy" / "lib" +++
      basePluginsDir / "IntelliLang" / "lib" +++
      basePluginsDir / "java-i18n" / "lib" +++
      basePluginsDir / "maven" / "lib" +++
      basePluginsDir / "properties" / "lib"
  val customJars = baseDirectories * "*.jar"
  customJars.classpath
}

unmanagedJars in Compile ++= (baseDirectory.value / "SDK/scalap" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value / "SDK/nailgun" * "*.jar").classpath

lazy val compiler_settings = Project("compiler-settings", file("compiler-settings"))

lazy val ScalaRunner = project.in(file("ScalaRunner"))

lazy val Runners = project.in(file("Runners")).dependsOn(ScalaRunner)

lazy val ScalaCommunity = project.in(file("")).dependsOn(compiler_settings, Runners).aggregate(jps_plugin)

lazy val intellij_hocon = Project("intellij-hocon", file("intellij-hocon")).dependsOn(ScalaCommunity)

lazy val intellij_scalastyle =
  Project("intellij-scalastyle", file("intellij-scalastyle")).dependsOn(ScalaCommunity)

lazy val jps_plugin = Project("scala-jps-plugin", file("jps-plugin")).dependsOn(compiler_settings)

lazy val idea_runner = Project("idea-runner", file("idea-runner"))

lazy val NailgunRunners = project.in(file("NailgunRunners")).dependsOn(ScalaRunner)

lazy val SBT = project.in(file("SBT")).dependsOn(intellij_hocon, ScalaCommunity)