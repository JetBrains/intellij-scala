package org.jetbrains.sbt.project.structure.data

case class TaskData(label: String, description: Option[String], rank: Int)

object TaskData:
  given XmlDeserializer[TaskData] = what =>
    val label = (what \ "label").text
    val description = (what \ "description").headOption.map(_.text)
    val rank = (what \ "rank").text.toInt
    Right(TaskData(label, description, rank))
