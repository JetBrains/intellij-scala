package org.jetbrains.plugins.scala.internal.bundle

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent
import org.jetbrains.plugins.scala.util.internal.I18nBundleContent.Entry

import java.io.PrintWriter
import java.nio.charset.Charset
import java.nio.file.{Files, Path}

object ExtractStringsFromPluginXml {
  case class PluginXmlInfo(resourcePath: Path, relativeXmlPath: String, bundleQualifiedPath: String) {
    def absoluteXmlPath: Path = resourcePath / relativeXmlPath
    def absoluteBundlePath: Path = resourcePath / (bundleQualifiedPath.replace(".", "/") + ".properties")
  }

  val scalaModDir: Path = ScalaBundleSorting.scalaModDir
  val sbtModDir: Path = ScalaBundleSorting.sbtModDir

  val scalaImplPluginXml: PluginXmlInfo = PluginXmlInfo(
    resourcePath = scalaModDir / "scala-impl/resources",
    relativeXmlPath = "scalaCommunity.scala-impl.xml",
    bundleQualifiedPath = "messages.ScalaInspectionBundle",
  )

  val sbtImplPluginXml: PluginXmlInfo = PluginXmlInfo(
    resourcePath = sbtModDir / "sbt-impl/resources",
    relativeXmlPath = "scalaCommunity.sbt-impl.xml",
    bundleQualifiedPath = "messages.SbtBundle",
  )

  val worksheetPluginXml: PluginXmlInfo = PluginXmlInfo(
    resourcePath = scalaModDir / "worksheet/resources",
    relativeXmlPath = "META-INF/worksheet.xml",
    bundleQualifiedPath = "messages.WorksheetBundle",
  )

  val allXmls = Seq(
    scalaImplPluginXml,
    sbtImplPluginXml,
    worksheetPluginXml,
  )

  def main(args: Array[String]): Unit =
    allXmls.foreach(processPluginXml)

  def processPluginXml(info: PluginXmlInfo): Unit = {
    val bundle = I18nBundleContent.read(info.absoluteBundlePath)

    val xmlContent = Files.readAllLines(info.absoluteXmlPath, Charset.defaultCharset()).toArray(new Array[String](_))

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

    val output = new PrintWriter(Files.newBufferedWriter(info.absoluteXmlPath))
    try newContext.foreach(output.println)
    finally output.close()
  }
}
