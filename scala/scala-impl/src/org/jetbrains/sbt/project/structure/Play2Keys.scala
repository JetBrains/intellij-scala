package org.jetbrains.sbt
package project.structure

import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.NonNls

import java.util
import scala.jdk.CollectionConverters._

object Play2Keys {
  val GLOBAL_TAG = "$global$"
  val ROOT_TAG = "root"
  private val ENTRY_SEQ_NAME = "entry"

  abstract class SettingKey[T] {
    val name: String
    val values: Map[String, T]
  }

  class StringXmlKey(override val name: String, override val values: Map[String, String]) extends SettingKey[String] {}
  class SeqStringXmlKey(override val name: String, override val values: Map[String, util.List[String]]) extends SettingKey[util.List[String]] {}

  object AllKeys {
    sealed trait ParsedValue[T] extends Serializable {
      def parsed: T
      override def toString: String = parsed.toString
    }
    case class StringParsedValue @PropertyMapping(Array("parsed")) (override val parsed: String) extends ParsedValue[String]

    case class SeqStringParsedValue @PropertyMapping(Array("parsed")) (override val parsed: util.List[String]) extends ParsedValue[util.List[String]]

    abstract class ParsedKey[T](val name: String) {
      def in(allKeys: Map[String, Map[String, ParsedValue[_]]]): Option[Map[String, ParsedValue[_]]] = allKeys get name

      def in(projectName: String, allKeys: Map[String, Map[String, ParsedValue[_]]]): Option[T] = {
        allIn(allKeys).find(_._1 == projectName).map(_._2)
      }

      def allIn(allKeys: Map[String, Map[String, ParsedValue[_]]]): Seq[(String, T)]

      override def toString: String = name + "_KEY"
    }

    class StringParsedKey(name: String) extends ParsedKey[String](name) {
      override def allIn(allKeys: Map[String, Map[String, ParsedValue[_]]]): Seq[(String, String)] = {
        in(allKeys) map { vs =>
            vs.toSeq flatMap {
              case (projectName, projectValue: StringParsedValue) => Some((projectName, projectValue.parsed))
              case _ => None
            }
        } getOrElse Seq.empty
      }
    }

    class SeqStringParsedKey(@NonNls name: String) extends ParsedKey[Seq[String]](name) {
      override def allIn(allKeys: Map[String, Map[String, ParsedValue[_]]]): Seq[(String, Seq[String])] = {
        in(allKeys) map { vs =>
            vs.toSeq flatMap {
              case (projectName, projectValue: SeqStringParsedValue) => Some((projectName, projectValue.parsed.asScala.toSeq))
              case _ => None
            }
        } getOrElse Seq.empty
      }
    }

    val PLAY_VERSION = new StringParsedKey("playVersion")

    val PROJECT_URI = new StringParsedKey("uri")

    val TEMPLATES_IMPORT = new SeqStringParsedKey("twirlTemplatesImports")
    val ROUTES_IMPORT = new SeqStringParsedKey("playRoutesImports")

    val PLAY_CONF_DIR = new StringParsedKey("playConf")
    val SOURCE_DIR = new StringParsedKey("sourceDirectory")
  }
}
