import java.io.PrintWriter

val Scala211 = "2.11.12"
val Scala212 = "2.12.17"
val Scala213 = "2.13.10"

val scalaVersions = Seq(Scala211, Scala212, Scala213)
val nativeScalaVersions = Seq(Scala212, Scala213)

LocalRootProject / name := "sbt-projectmatrix-with-source-generators-sbt2-jvm-native"
LocalRootProject / scalaVersion := Scala212

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
  .nativePlatform(scalaVersions = nativeScalaVersions)

lazy val downstreamBothPlatforms = (projectMatrix in file("downstreamBothPlatforms"))
  .settings(commonSettings(true) *)
  .dependsOn(upstreamBothPlatforms)
  .jvmPlatform(scalaVersions = scalaVersions)
  .nativePlatform(scalaVersions = nativeScalaVersions)

def generateClassName(isDownstream: Boolean, isTest: Boolean): String = {
  val className = if (isDownstream) "PersonGeneratedDownstream" else "PersonGeneratedUpstream"
  if (isTest) s"${className}Test" else className
}

def createFile(mangedSourcesRoot: File, className: String): Seq[File] = {
  val file = new File(mangedSourcesRoot, s"com/example/$className.scala")
  file.getParentFile.mkdirs()
  val fileContent =
    s"""package com.example
       |
       |case class $className(name: String, age: Int)
       |""".stripMargin
  scala.util.Using.resource(new PrintWriter(file))(_.println(fileContent))
  Seq(file)
}

def commonSettings(isDownstream: Boolean): Seq[_root_.sbt.Def.SettingsDefinition] = Seq(
  //This lines auto-generates sources during project import
  update := Def.task {
    (Compile / managedSources).value
    (Test / managedSources).value
    update.value
  }.value,

  //generate single class file in managed sources dir
  Compile / sourceGenerators += Def.task {
    val className = generateClassName(isDownstream, isTest = false)
    val mangedSourcesRoot = (Compile / sourceManaged).value
    createFile(mangedSourcesRoot, className)
  }.taskValue,

  //generate single class file in managed test sources dir
  Test / sourceGenerators += Def.task {
    if (isDownstream) {
      val className = generateClassName(isDownstream, isTest = true)
      val mangedSourcesRoot = (Test / sourceManaged).value
      createFile(mangedSourcesRoot, className)
    } else {
      Seq.empty
    }
  }.taskValue,
)
