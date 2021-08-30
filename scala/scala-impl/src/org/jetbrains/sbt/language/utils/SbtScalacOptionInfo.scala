package org.jetbrains.sbt.language.utils

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import spray.json.DefaultJsonProtocol._
import spray.json._

final case class SbtScalacOptionInfo(flag: String, description: String, scalaVersions: Set[ScalaLanguageLevel]) {
  def quoted: String = s""""$flag""""
}

object SbtScalacOptionInfo {
  implicit object ScalaLanguageLevelJsonFormat extends JsonFormat[ScalaLanguageLevel] {
    def write(level: ScalaLanguageLevel): JsValue =
      JsString(level.getName)

    def read(value: JsValue): ScalaLanguageLevel = value match {
      case JsString(version) =>
        ScalaLanguageLevel.findByVersion(version)
          .getOrElse(deserializationError(s"Scala language level `$version` not found"))
      case _ => deserializationError("Scala language level expected")
    }
  }

  implicit val sbtScalacOptionInfoJsonFormat: RootJsonFormat[SbtScalacOptionInfo] =
    jsonFormat3(SbtScalacOptionInfo.apply)
}
