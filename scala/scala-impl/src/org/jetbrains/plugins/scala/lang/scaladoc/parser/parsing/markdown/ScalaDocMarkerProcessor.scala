package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.GFMMarkerProcessor
import org.intellij.markdown.flavours.gfm.table.GitHubTableMarkerProvider
import org.intellij.markdown.parser.constraints.{CommonMarkdownConstraints, MarkdownConstraints}
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers._
import org.intellij.markdown.parser.{LookaheadText, MarkerProcessor, ProductionHolder}

import java.{util => ju}

class ScalaDocMarkerProcessor(productionHolder: ProductionHolder, constraints: CommonMarkdownConstraints)
  extends CommonMarkMarkerProcessor(productionHolder, constraints) {

  private val gfmProcessor = new GFMMarkerProcessor(productionHolder, constraints)

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

    // gfm
    new GitHubTableMarkerProvider,
  )

  private val m = {
    val m = gfmProcessor.getClass.getDeclaredMethods.find(_.getName == "populateConstraintsTokens").get
    m.setAccessible(true)
    m
  }
  override def populateConstraintsTokens(pos: LookaheadText#Position, constraints: MarkdownConstraints, productionHolder: ProductionHolder): Unit =
    m.invoke(gfmProcessor, pos, constraints, productionHolder)

  override def getMarkerBlockProviders: ju.List[MarkerBlockProvider[MarkerProcessor.StateInfo]] = markerBlockProviders
}