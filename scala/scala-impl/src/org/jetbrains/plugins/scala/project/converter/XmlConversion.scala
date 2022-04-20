package org.jetbrains.plugins.scala.project.converter

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element

import scala.xml.{Elem, PrettyPrinter}

trait XmlConversion {
  protected def formatXml(element: Element): String =
    JDOMUtil.write(element)

  protected def formatXml(element: Elem): String = {
    val printer = new PrettyPrinter(180, 2)
    printer.format(element)
  }

  protected def parseXml(xml: String): Element = {
    JDOMUtil.load(xml)
  }

  protected def asJava(element: Elem): Element = parseXml(formatXml(element))
}
