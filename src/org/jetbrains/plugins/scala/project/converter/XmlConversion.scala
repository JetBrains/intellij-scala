package org.jetbrains.plugins.scala.project.converter

import java.io.StringReader

import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jdom.output.{Format, XMLOutputter}

import scala.xml.{Elem, PrettyPrinter}

/**
* @author Pavel Fatin
*/
trait XmlConversion {
  protected def formatXml(element: Element): String = {
    val outputter = new XMLOutputter(Format.getPrettyFormat)
    outputter.outputString(element)
  }

  protected def formatXml(element: Elem): String = {
    val printer = new PrettyPrinter(180, 2)
    printer.format(element)
  }

  protected def parseXml(xml: String): Element = {
    val builder = new SAXBuilder()
    val document = builder.build(new StringReader(xml))
    document.detachRootElement()
  }

  protected def asJava(element: Elem): Element = parseXml(formatXml(element))
}
