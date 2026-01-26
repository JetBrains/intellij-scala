package org.jetbrains.sbt.language.utils

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.{ArgType, Deprecation}
import spray.json.DefaultJsonProtocol.*
import spray.json.*

import scala.collection.immutable.{SortedMap, SortedSet}

final case class SbtScalacOptionInfo(flag: String,
                                     descriptions: Map[ScalaLanguageLevel, String],
                                     choices: Map[ScalaLanguageLevel, Set[String]],
                                     argType: ArgType,
                                     scalaVersions: Set[ScalaLanguageLevel],
                                     defaultValue: Option[String],
                                     deprecations: Map[ScalaLanguageLevel, Deprecation],
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

  implicit object SbtScalacOptionInfoJsonFormat extends JsonFormat[SbtScalacOptionInfo] {
    private val inner: RootJsonFormat[SbtScalacOptionInfo] =
      jsonFormat7(SbtScalacOptionInfo.apply)

    override def read(json: JsValue): SbtScalacOptionInfo = inner.read(json)
    override def write(obj: SbtScalacOptionInfo): JsValue = {
      scala.math.Ordering.String
      // don't use .copy, so that we catch changes to SbtScalacOptionInfo when it's changed
      val sortedObj = SbtScalacOptionInfo(
        flag = obj.flag,
        descriptions = obj.descriptions.to(SortedMap),
        choices = obj.choices.view.mapValues(_.to(SortedSet)).to(SortedMap),
        argType = obj.argType,
        scalaVersions = obj.scalaVersions.to(SortedSet),
        defaultValue = obj.defaultValue,
        deprecations = obj.deprecations.to(SortedMap),
      )

      // We need this because for some reason objects are converted from SortedMaps to HashMaps in spray during writing
      def sorted(value: JsValue): JsValue = {
        value match {
          case JsObject(fields) => JsObject(fields.view.mapValues(sorted).to(SortedMap))
          case JsArray(elements) => JsArray(elements.map(sorted))
          case _ => value
        }
      }

      sorted(inner.write(sortedObj))
    }
  }

  case class Deprecation(msg: String, replacedWith: Option[String])

  implicit object DeprecationFormat extends JsonFormat[Deprecation] {
    override def read(json: JsValue): Deprecation = {
      json match {
        case JsTrue => Deprecation("", None)
        case JsObject(fields) =>
          val msg = fields.get("msg").map(_.convertTo[String]).getOrElse("")
          val replacedWith = fields.get("replacedWith").map(_.convertTo[String])
          Deprecation(msg, replacedWith)
        case _ =>
          deserializationError("Expected object or true, got: " + json)
      }
    }

    override def write(obj: Deprecation): JsValue = {
      var result = SortedMap.empty[String, JsValue]
      if (obj.msg != "") {
        result += "msg" -> JsString(obj.msg)
      }
      obj.replacedWith.foreach { replacedWith =>
          result += "replacedWith" -> JsString(replacedWith)
      }

      if (result.isEmpty) {
        JsTrue
      } else {
        JsObject(result)
      }
    }
  }
}
