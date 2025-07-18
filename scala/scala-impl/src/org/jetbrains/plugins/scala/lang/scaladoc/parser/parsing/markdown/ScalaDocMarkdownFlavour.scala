package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html._
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.lexer._MarkdownLexer
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.impl._
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MarkdownCompanionProxy

import java.net.URI
import java.util
import scala.jdk.CollectionConverters._

class ScalaDocMarkdownFlavour extends CommonMarkFlavourDescriptor {
  override def getMarkerProcessorFactory: MarkerProcessorFactory = new MarkerProcessorFactory {
    override def createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor[_] = {
      new ScalaDocMarkerProcessor(productionHolder, ScalaDocMarkdownConstraints.BASE)
    }
  }

  override def createHtmlGeneratingProviders(linkMap: LinkMap, uri: URI): java.util.Map[IElementType, GeneratingProvider] = {
    // TODO: Some are unimplemented.
    val parent = super.createHtmlGeneratingProviders(linkMap, uri)
    parent.putAll(
      Map(
        // ScalaDoc tags
        ScalaDocTagMarkerBlock.TAG_BLOCK -> new SimpleTagProvider("div"),
      ).asJava
    )

    parent
  }
}