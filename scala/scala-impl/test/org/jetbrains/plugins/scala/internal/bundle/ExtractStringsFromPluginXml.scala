package org.jetbrains.plugins.scala.internal.bundle

import java.io.{File, PrintWriter}

import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent.Entry

import scala.io.Source

object ExtractStringsFromPluginXml {
  case class PluginXmlInfo(resourcePath: String, relativeXmlPath: String, bundleQualifiedPath: String) {
    def absoulteXmlPath: String = resourcePath + relativeXmlPath
    def absoluteBundlePath: String = resourcePath + bundleQualifiedPath.replace(".", "/") + ".properties"
  }

  val scalaModDir: String = ScalaBundleSorting.scalaModDir

  val communityPluginXml: PluginXmlInfo = PluginXmlInfo(
    resourcePath = scalaModDir + "scala-impl/resources/",
    relativeXmlPath = "META-INF/scala-plugin-common.xml",
    bundleQualifiedPath = "messages.ScalaInspectionBundle",
  )

  val sbtPluginXml: PluginXmlInfo = PluginXmlInfo(
    resourcePath = scalaModDir + "scala-impl/resources/",
    relativeXmlPath = "META-INF/SBT.xml",
    bundleQualifiedPath = "messages.SbtBundle",
  )

  val worksheetPluginXml: PluginXmlInfo = PluginXmlInfo(
    resourcePath = scalaModDir + "worksheet/resources/",
    relativeXmlPath = "META-INF/worksheet.xml",
    bundleQualifiedPath = "messages.WorksheetBundle",
  )

  val allXmls = Seq(
    communityPluginXml,
    sbtPluginXml,
    worksheetPluginXml,
  )

  def main(args: Array[String]): Unit =
    allXmls.foreach(processPluginXml)

  def processPluginXml(info: PluginXmlInfo): Unit = {
    val bundle = I18nBundleContent.read(info.absoluteBundlePath)

    val xmlContent = {
      val source = Source.fromFile(info.absoulteXmlPath)
      try source.getLines().toArray
      finally source.close()
    }

    val newEntries = Seq.newBuilder[Entry]
    val displayNameRegex = "displayName=\"(.*?)\"".r

    val newContext = xmlContent.map(line => displayNameRegex.replaceAllIn(line,
      regex => {
        val oldText = regex.group(1)
        val key = "displayname." + I18nBundleContent.convertStringToKey(oldText)
        val text = I18nBundleContent.escapeText(oldText, hasArgs = false)
        newEntries += Entry(key, text, info.relativeXmlPath)
        println(key + "=" + text)
        s"""bundle="${info.bundleQualifiedPath}"
           |key="$key"
           |""".stripMargin
      }
    ))

    val newBundle = I18nBundleContent(bundle.entries ++ newEntries.result())
    newBundle.sorted.writeTo(info.absoluteBundlePath)

    val output = new PrintWriter(new File(info.absoulteXmlPath))
    try newContext.foreach(output.println)
    finally output.close()
  }
}
