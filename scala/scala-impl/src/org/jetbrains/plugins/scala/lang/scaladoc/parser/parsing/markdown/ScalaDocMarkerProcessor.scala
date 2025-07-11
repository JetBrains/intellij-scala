package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers._

import java.util
import scala.jdk.CollectionConverters._

class ScalaDocMarkerProcessor(productionHolder: ProductionHolder, constraints: MarkdownConstraints)
  extends CommonMarkMarkerProcessor(productionHolder, constraints) {

  private val markerBlockProviders = List(
    new CodeBlockProvider,
    new HorizontalRuleProvider,
    new CodeFenceProvider,
    new ScalaDocCodeFenceProvider,
    new ScalaDocTagMarkerBlockProvider,
    new SetextHeaderProvider,
    new BlockQuoteProvider,
    new ListMarkerProvider,
    new AtxHeaderProvider,
    new HtmlBlockProvider,
    new LinkReferenceDefinitionProvider,
  ).asJava

  override def getMarkerBlockProviders: java.util.List[MarkerBlockProvider[MarkerProcessor.StateInfo]] = markerBlockProviders
}