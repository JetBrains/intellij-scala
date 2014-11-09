package org.jetbrains.plugins.scala
package project.converter

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{JavadocOrderRootType, OrderRootType}
import org.jdom.{Attribute, Element}
import org.jdom.xpath.XPath
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
private case class LibraryData(name: String, classes: Seq[String], sources: Seq[String], docs: Seq[String]) {
  def classesAsFileUrls: Seq[String] =
    classes.map(url => "file" + url.substring(3, url.length - 2))
}

private object LibraryData {
  def apply(prototype: Library): LibraryData = {
    LibraryData(prototype.getName,
      prototype.getUrls(OrderRootType.CLASSES).toSeq,
      prototype.getUrls(OrderRootType.SOURCES).toSeq,
      prototype.getUrls(JavadocOrderRootType.getInstance).toSeq)
  }

  def apply(element: Element): LibraryData = {
    def urls(kind: String) = XPath.selectNodes(element, kind + "/root/@url").asScala
            .map(_.asInstanceOf[Attribute].getValue)

    LibraryData(element.getAttributeValue("name"), urls("CLASSES"), urls("SOURCES"), urls("JAVADOC"))
  }

  def empty: LibraryData = LibraryData("", Seq.empty, Seq.empty, Seq.empty)
}
