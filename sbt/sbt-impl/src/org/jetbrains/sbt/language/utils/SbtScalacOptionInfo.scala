package org.jetbrains.sbt.language.utils

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType
import spray.json.DefaultJsonProtocol._
import spray.json._

final case class SbtScalacOptionInfo(flag: String,
                                     descriptions: Map[ScalaLanguageLevel, String],
                                     choices: Map[ScalaLanguageLevel, Set[String]],
                                     argType: ArgType,
                                     scalaVersions: Set[ScalaLanguageLevel],
                                     defaultValue: Option[String],
                                    ) {
  def getText: String = argType match {
    case ArgType.OneSeparate => s""""$flag", """""
    case ArgType.OneAfterColon | ArgType.Multiple => s""""$flag:""""
    case _ => s""""$flag""""
  }
}

object SbtScalacOptionInfo {
  sealed trait ArgType
  object ArgType {
    case object No extends ArgType
    case object OneSeparate extends ArgType
    case object OneAfterColon extends ArgType
    case object Multiple extends ArgType
    final case class OneAfterPrefix(prefix: String) extends ArgType

    implicit object ArgTypeJsonFormat extends JsonFormat[ArgType] {
      override def write(argType: ArgType): JsValue = argType match {
        case OneAfterPrefix(prefix) =>
          JsObject("type" -> JsString("OneAfterPrefix"), "prefix" -> JsString(prefix))
        case _ => JsString(argType.toString)
      }

      override def read(json: JsValue): ArgType = json match {
        case JsString("No") => No
        case JsString("OneSeparate") => OneSeparate
        case JsString("OneAfterColon") => OneAfterColon
        case JsString("Multiple") => Multiple
        case JsObject(fields) if fields.keySet.contains("type") =>
          fields.get("type") match {
            case Some(JsString("OneAfterPrefix")) => fields.get("prefix") match {
              case Some(JsString(prefix)) => OneAfterPrefix(prefix)
              case _ => throw DeserializationException("Prefix string of scalac option argument type not found")
            }
            case _ => throw DeserializationException("Unexpected scalac option argument type")
          }
        case _ =>
          throw DeserializationException("Scalac option argument type expected")
      }
    }
  }

  implicit object ScalaLanguageLevelJsonFormat extends JsonFormat[ScalaLanguageLevel] {
    def write(level: ScalaLanguageLevel): JsValue =
      JsString(level.getVersion)

    def read(value: JsValue): ScalaLanguageLevel = value match {
      case JsString(version) =>
        ScalaLanguageLevel.findByVersion(version)
          .getOrElse(deserializationError(s"Scala language level `$version` not found"))
      case _ => deserializationError("Scala language level expected")
    }
  }

  implicit val sbtScalacOptionInfoJsonFormat: RootJsonFormat[SbtScalacOptionInfo] =
    jsonFormat6(SbtScalacOptionInfo.apply)
}
