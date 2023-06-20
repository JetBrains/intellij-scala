ThisBuild / name := "sbt-crossproject-test-project-with-generated-sources"

lazy val upstreamPure =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(commonSettings(false) *)

lazy val downstreamPure =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(commonSettings(true) *)
    .dependsOn(upstreamPure)

lazy val upstreamFull =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .settings(commonSettings(false) *)

lazy val downstreamFull =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .settings(commonSettings(true) *)
    .dependsOn(upstreamFull)

def commonSettings(isDownstream: Boolean): Seq[_root_.sbt.Def.SettingsDefinition] = Seq(
  scalaVersion := "2.13.11",

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

    import java.io.PrintWriter
    scala.util.Using.resource(new PrintWriter(file))(_.println(fileContent))

    Seq(file)
  }.taskValue,
)