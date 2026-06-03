package org.jetbrains.sbt.project.structure.data

case class SettingData(label: String, description: Option[String], rank: Int, stringValue: Option[String])

object SettingData:
  given XmlDeserializer[SettingData] = what =>
    val label = (what \ "label").text
    val description = (what \ "description").headOption.map(_.text)
    val rank = (what \ "rank").text.toInt
    val stringValue = (what \ "value").headOption.map(_.text)
    Right(SettingData(label, description, rank, stringValue))
