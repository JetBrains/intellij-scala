package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import java.{util => ju}

import com.intellij.lang.WhitespacesAndCommentsBinder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[Annotations]] ::= { [[Annotation]] }
 */
private[parsing] object Annotations extends ParsingRule {

  private val LeftEdgeBinder: WhitespacesAndCommentsBinder =
    (tokens: ju.List[_ <: IElementType], _: Boolean, _: WhitespacesAndCommentsBinder.TokenTextGetter) => tokens.size

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    parse()
    true
  }

  def parseOnTheSameLine()(implicit builder: ScalaPsiBuilder): Boolean = {
    parse(builder.newlineBeforeCurrentToken)
    true
  }

  def parseAndBindToLeft()(implicit builder: ScalaPsiBuilder): Unit = {
    val marker = parse()
    marker.setCustomEdgeTokenBinders(LeftEdgeBinder, null)
  }

  def parseEmptyAndBindLeft()(implicit builder: ScalaPsiBuilder): Unit = {
    val marker = builder.mark()
    marker.done(ScalaElementType.ANNOTATIONS)
    marker.setCustomEdgeTokenBinders(LeftEdgeBinder, null)
  }

  private def parse(newlineBeforeCurrentToken: Boolean = false)
                   (implicit builder: ScalaPsiBuilder) = {
    val marker = builder.mark()

    if (!newlineBeforeCurrentToken) {
      while (Annotation()) {}
    }
    marker.done(ScalaElementType.ANNOTATIONS)

    marker
  }
}
