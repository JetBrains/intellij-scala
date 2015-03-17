package org.jetbrains.sbt
package project.structure

import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.{SeqStringParsedValue, StringParsedValue, ParsedValue}

import scala.collection.mutable
import scala.xml.Text

/**
 * User: Dmitry.Naydanov
 * Date: 26.09.14.
 */
object Play2Keys {
  val GLOBAL_TAG = "$global$"
  val ROOT_TAG = "root"
  private val ENTRY_SEQ_NAME = "entry"

  abstract class SettingKey[T] {
    val name: String
    val values: Map[String, T]
  }

  class StringXmlKey(val name: String, val values: Map[String, String]) extends SettingKey[String] {}
  class SeqStringXmlKey(val name: String, val values: Map[String, Seq[String]]) extends SettingKey[Seq[String]] {}

  object KeyExtractor {
    def extract(elem: scala.xml.Node): Option[SettingKey[_]] = {
      if (elem.isInstanceOf[Text] || elem.label == "#PCDATA") return None

      val keyName = elem.label
      val children = elem.child.filterNot(_.text.forall(c => c == '\n' || c == ' '))

      if (children.forall(_.child.forall{case _: Text => true; case _ => false})) {
        Some(new StringXmlKey(keyName, children.map(projectKey => (projectKey.label, projectKey.text)).toMap))
      } else if (children.forall(_.child.forall(node => node.label == ENTRY_SEQ_NAME || node.isInstanceOf[Text]))) {
        val values = children.flatMap{
          case _: Text => None
          case projectKey => Some((projectKey.label, projectKey \ ENTRY_SEQ_NAME map (_.text)))
        }.toMap
        Some(new SeqStringXmlKey(keyName, values))
      } else None
    }
  }

  object KeyTransformer {
    def transform(keys: Seq[SettingKey[_]]): Map[String, Map[String, ParsedValue[_]]] = {
      val map = mutable.HashMap[String, Map[String, ParsedValue[_]]]()

      keys foreach {
        case str: StringXmlKey =>
          map.put(str.name, str.values.map {
            case (k, v) => (k, new StringParsedValue(v))
          })
        case seqStr: SeqStringXmlKey =>
          map.put(seqStr.name, seqStr.values map {
            case (k, v) => (k, new SeqStringParsedValue(v))
          })
        case _ =>
      }

      map.toMap
    }
  }


  object AllKeys {
    abstract class ParsedValue[T](val parsed: T) extends Serializable {
      override def toString: String = parsed.toString
    }
    class StringParsedValue(parsed: String) extends ParsedValue[String](parsed)
    class SeqStringParsedValue(parsed: Seq[String]) extends ParsedValue[Seq[String]](parsed)

    abstract class ParsedKey[T](val name: String) {
      def in(allKeys: Map[String, Map[String, ParsedValue[_]]]) = allKeys get name

      def in(projectName: String, allKeys: Map[String, Map[String, ParsedValue[_]]]): Option[T] = {
        allIn(allKeys).find(_._1 == projectName).map(_._2)
      }

      def allIn(allKeys: Map[String, Map[String, ParsedValue[_]]]): Seq[(String, T)]

      override def toString: String = name + "_KEY"
    }

    class StringParsedKey(name: String) extends ParsedKey[String](name) {
      override def allIn(allKeys: Map[String, Map[String, ParsedValue[_]]]): Seq[(String, String)] = {
        in(allKeys) map {
          case vs => vs.toSeq flatMap {
            case (projectName, projectValue: StringParsedValue) => Some((projectName, projectValue.parsed))
            case _ => None
          }
        } getOrElse Seq.empty
      }
    }

    class SeqStringParsedKey(name: String) extends ParsedKey[Seq[String]](name) {
      override def allIn(allKeys: Map[String, Map[String, ParsedValue[_]]]): Seq[(String, Seq[String])] = {
        in(allKeys) map {
          case vs => vs.toSeq flatMap {
            case (projectName, projectValue: SeqStringParsedValue) => Some((projectName, projectValue.parsed))
            case _ => None
          }
        } getOrElse Seq.empty
      }
    }

    val PLAY_VERSION = new StringParsedKey("playVersion")

    val PROJECT_URI = new StringParsedKey("uri")

    val TEMPLATES_IMPORT = new SeqStringParsedKey("twirlTemplatesImports")
    val ROUTES_IMPORT = new SeqStringParsedKey("playRoutesImports")

    val TEST_OPTIONS = new StringParsedKey("testOptions")
//    val TEMPLATE_FORMATS = "playTemplatesFormats"

    val PLAY_CONF_DIR = new StringParsedKey("playConf")
    val SOURCE_DIR = new StringParsedKey("sourceDirectory")
  }
}
