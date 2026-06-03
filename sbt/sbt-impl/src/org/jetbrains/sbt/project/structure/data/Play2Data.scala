package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.Helpers.!

case class Play2Data(playVersion: Option[String],
                     templatesImports: Seq[String],
                     routesImports: Seq[String],
                     confDirectory: Option[InterpretablePath],
                     sourceDirectory: InterpretablePath)

object Play2Data:
  given PathConstructor[String] => XmlDeserializer[Play2Data] = what =>
    val playVersion = (what \ "version").map(_.text).headOption
    val templatesImports = (what \ "templatesImports" \ "import").map(_.text)
    val routesImports = (what \ "routesImports" \ "import").map(_.text)
    val confDirectory = (what \ "confDirectory").map(_.text).headOption
    val sourceDirectory = (what ! "sourceDirectory").text
    Right(Play2Data(playVersion, templatesImports, routesImports, confDirectory.map(InterpretablePath.construct), InterpretablePath.construct(sourceDirectory)))
