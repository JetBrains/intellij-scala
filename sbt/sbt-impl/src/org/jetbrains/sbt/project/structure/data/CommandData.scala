package org.jetbrains.sbt.project.structure.data

case class CommandData(name: String, help: Seq[(String,String)])

object CommandData:
  given XmlDeserializer[CommandData] = what =>
    val name = (what \ "name").text
    val help = (what \ "help").map: helpNode =>
      val cmd = (helpNode \ "cmd").text
      val description = (helpNode \ "desc").text
      (cmd, description)
    Right(CommandData(name, help))
