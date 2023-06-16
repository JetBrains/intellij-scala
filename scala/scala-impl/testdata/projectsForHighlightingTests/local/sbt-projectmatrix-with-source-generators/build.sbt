import java.io.PrintWriter

ThisBuild / name := "sbt-projectmatrix-with-source-generators"

val scalaVersions = Seq("2.11.12", "2.12.17", "2.13.10")

lazy val upstream = (projectMatrix in file("upstream"))
  .settings(commonSettings(false) *)
  .jvmPlatform(scalaVersions = scalaVersions)

lazy val downstream = (projectMatrix in file("downstream"))
  .settings(commonSettings(true) *)
  .dependsOn(upstream)
  .jvmPlatform(scalaVersions = scalaVersions)

lazy val upstreamBothPlatforms = (projectMatrix in file("upstreamBothPlatforms"))
  .settings(commonSettings(false) *)
  .jvmPlatform(scalaVersions = scalaVersions)
  .jsPlatform(scalaVersions = scalaVersions)

lazy val downstreamBothPlatforms = (projectMatrix in file("downstreamBothPlatforms"))
  .settings(commonSettings(true) *)
  .dependsOn(upstreamBothPlatforms)
  .jvmPlatform(scalaVersions = scalaVersions)
  .jsPlatform(scalaVersions = scalaVersions)

def commonSettings(isDownstream: Boolean): Seq[_root_.sbt.Def.SettingsDefinition] = Seq(
  //This lines auto-generates sources during project import
  update := Def.task {
    (Compile / managedSources).value
    update.value
  }.value,

  //generate single class file in managed sources dir
  Compile / sourceGenerators += Def.task {
    val mangedSourcesRoot = (Compile / sourceManaged).value

    val className = if (isDownstream) "PersonGeneratedDownstream" else "PersonGeneratedUpstream"
    val file = new File(mangedSourcesRoot, s"com/example/$className.scala")
    file.getParentFile.mkdirs()
    val fileContent =
      s"""package com.example
         |
         |case class $className(name: String, age: Int)
         |""".stripMargin
    scala.util.Using.resource(new PrintWriter(file))(_.println(fileContent))

    Seq(file)
  }.taskValue,
)