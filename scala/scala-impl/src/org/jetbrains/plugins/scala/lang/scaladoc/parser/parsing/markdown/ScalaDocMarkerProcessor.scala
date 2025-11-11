package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers._
import org.intellij.markdown.parser.{MarkerProcessor, ProductionHolder}

import java.{util => ju}

class ScalaDocMarkerProcessor(productionHolder: ProductionHolder, constraints: MarkdownConstraints)
  extends CommonMarkMarkerProcessor(productionHolder, constraints) {

  private val markerBlockProviders = ju.List.of(
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
  )

  override def getMarkerBlockProviders: ju.List[MarkerBlockProvider[MarkerProcessor.StateInfo]] = markerBlockProviders
}