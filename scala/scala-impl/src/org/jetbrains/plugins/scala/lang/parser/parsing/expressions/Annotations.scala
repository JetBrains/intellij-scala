package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.WhitespacesAndCommentsBinder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import java.{util => ju}

/**
 * [[Annotations]] ::= { [[Annotation]] }
 */
private[parsing] object Annotations extends ParsingRule {

  private val LeftEdgeBinder: WhitespacesAndCommentsBinder =
    (tokens: ju.List[_ <: IElementType], _: Boolean, _: WhitespacesAndCommentsBinder.TokenTextGetter) => tokens.size

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    parseAnnotations()
    true
  }

  def parseForConstructor()(implicit builder: ScalaPsiBuilder): Boolean = {
    parseAnnotations(builder.newlineBeforeCurrentToken, forConstructor = true)
    true
  }

  def parseAndBindToLeft()(implicit builder: ScalaPsiBuilder): Unit = {
    val marker = parseAnnotations()
    marker.setCustomEdgeTokenBinders(LeftEdgeBinder, null)
  }

  def parseEmptyAndBindLeft()(implicit builder: ScalaPsiBuilder): Unit = {
    val marker = builder.mark()
    marker.done(ScalaElementType.ANNOTATIONS)
    marker.setCustomEdgeTokenBinders(LeftEdgeBinder, null)
  }

  private def parseAnnotations(newlineBeforeCurrentToken: Boolean = false, forConstructor: Boolean = false)
                              (implicit builder: ScalaPsiBuilder) = {
    val marker = builder.mark()

    if (!newlineBeforeCurrentToken) {
      while (Annotation(forConstructor = forConstructor)) {}
    }
    marker.done(ScalaElementType.ANNOTATIONS)

    marker
  }
}
