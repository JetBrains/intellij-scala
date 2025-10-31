package org.jetbrains.sbt.project.structure.data

case class CompilerOptions(configuration: Configuration, options: Seq[String])

object CompilerOptions:
  given XmlDeserializer[CompilerOptions] = what =>
    val configuration = (what \ "configuration").text
    val options = (what \ "option").map(_.text)
    Right(CompilerOptions(Configuration(configuration), options))
