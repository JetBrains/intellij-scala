package org.jetbrains.plugins.scala.util

import java.util

import org.jdom.Element
import org.jetbrains.plugins.scala.extensions.ObjectExt

import scala.jdk.CollectionConverters._

class JdomExternalizerMigrationHelper private (element: org.jdom.Element) {

  def migrateString(key: String)(patcher: String => Unit): Unit =
    readString(key).foreach(patcher)

  def migrateBool(key: String)(patcher: Boolean => Unit): Unit =
    readString(key).foreach(x => patcher(x.toBoolean))

  def migrateInt(key: String)(patcher: Int => Unit): Unit =
    readString(key).foreach(x => patcher(x.toInt))

  def migrateMap(mapKey: String, elemKey: String, map: java.util.Map[String, String]): Unit = {
    val mapElement = element.getChild(mapKey).toOption
    val mapEntries = mapElement.map(_.getChildren(elemKey).asScala).getOrElse(Nil)

    mapEntries.foreach { e =>
      val key = e.getAttributeValue(NAME_KEY).toOption.getOrElse("")
      val value = e.getAttributeValue(VALUE_KEY).toOption.getOrElse("")
      map.put(key, value)
    }
  }

  def migrateArray(arrayKey: String, elemKey: String)(patcher: Array[String] => Unit): Unit = {
    val map = new util.HashMap[String, String]()
    // array is a map from index to array element, e.g. <pattern name="0" value="MyValue" />
    migrateMap(arrayKey, elemKey, map)
    val array = map.asScala.toArray.sortBy(_._1).map(_._2)
    patcher(array)
  }

  private def needsMigration(): Boolean = element.getChild(SETTING_KEY) != null

  private def readString(key: String): Option[String] = {
    element
      .getChildren(SETTING_KEY)
      .asScala
      .find(key == _.getAttributeValue(NAME_KEY))
      .flatMap(_.getAttributeValue(VALUE_KEY).toOption)
  }


  private final val SETTING_KEY = "setting"
  private final val VALUE_KEY   = "value"
  private final val NAME_KEY    = "name"

}

object JdomExternalizerMigrationHelper {
  def apply(element: Element)(runner: JdomExternalizerMigrationHelper => Unit): Unit = {
    val helper = new JdomExternalizerMigrationHelper(element)
    if (helper.needsMigration())
      runner(helper)
  }
}
