package org.jetbrains.plugins.scala
package project.converter

import org.jdom.xpath.XPath
import org.jdom.{Attribute, Element}

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

private class FacetProperties(element: Element) {
  def option(key: String): Option[String] = Option {
    XPath.selectSingleNode(element, s"configuration/option[@name='$key']/@value").asInstanceOf[Attribute]: @nowarn("cat=deprecation")
  }.map(_.getValue)

  def string(key: String, default: String): String = option(key).getOrElse(default)

  def seq(key: String, default: Seq[String] = Seq.empty): Seq[String] =
    option(key).filterNot(_.isEmpty).map(_.split(' ').toSeq).getOrElse(default)

  def boolean(key: String, default: Boolean = false): Boolean = option(key).map(_.toBoolean).getOrElse(default)

  def int(key: String, default: Int): Int = option(key).map(_.toInt).getOrElse(default)

  @nowarn("cat=deprecation")
  def array(key: String): Seq[String] = {
    XPath.selectNodes(element, s"configuration/option[@name='$key']/array/option/@value").asScala.iterator
      .map(_.asInstanceOf[Attribute].getValue)
      .toSeq
  }
}
