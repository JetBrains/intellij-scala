package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserializeNodeSeq

case class ScalaData(
  organization: String,
  version: String,
  libraryJars: Seq[InterpretablePath],
  compilerJars: Seq[InterpretablePath],
  extraJars: Seq[InterpretablePath],
  compilerBridgeBinaryJar: Option[InterpretablePath],
  options: Seq[CompilerOptions]
):
  def allJars: Seq[InterpretablePath] = libraryJars ++ compilerJars ++ extraJars
  def allCompilerJars: Seq[InterpretablePath] = libraryJars ++ compilerJars

object ScalaData:
  given PathConstructor[String] => XmlDeserializer[ScalaData] = what =>
    val organization = (what \ "organization").headOption.map(_.text).getOrElse("org.scala-lang")
    val version = (what \ "version").text

    val libraryJars = (what \ "libraryJars" \ "jar").map(_.text).map(InterpretablePath.construct)
    val compilerJars = (what \ "compilerJars" \ "jar").map(_.text).map(InterpretablePath.construct)
    val extraJars = (what \ "extraJars" \ "jar").map(_.text).map(InterpretablePath.construct)
    val compilerBridgeBinaryJar = (what \ "compilerBridgeBinaryJar").headOption.map(_.text).map(InterpretablePath.construct)

    val options = (what \ "compilerOptions").deserializeNodeSeq[CompilerOptions]
    Right(ScalaData(
      organization,
      version,
      libraryJars,
      compilerJars,
      extraJars,
      compilerBridgeBinaryJar,
      options
    ))
