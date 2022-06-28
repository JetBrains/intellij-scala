package org.jetbrains.plugins.scala.lang
package parser
package parsing
package top

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{kWITH, tCOMMA}
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

sealed abstract class Parents(val allowCommaSeparatedParentsInScala3: Boolean = true) extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    if (parseParent()) {
      parseNextSimpleTypes()
    }

    marker.done(ScalaElementType.TEMPLATE_PARENTS)
    true
  }

  @tailrec
  private def parseNextSimpleTypes()(implicit builder: ScalaPsiBuilder): Unit =
    builder.getTokenType match {
      case `kWITH` | CommaIfAllowed() =>
        builder.advanceLexer() // Ate with
        if (parseParent()) {
          parseNextSimpleTypes()
        }
      case _ =>
    }

  private def parseParent()(implicit builder: ScalaPsiBuilder): Boolean = {
    val result = Constructor()

    if (!result) {
      builder.error(ScalaBundle.message("identifier.expected"))
    }

    result
  }

  private object CommaIfAllowed {
    def unapply(element: IElementType)(implicit builder: ScalaPsiBuilder): Boolean =
      element == tCOMMA && builder.isScala3 && allowCommaSeparatedParentsInScala3
  }
}


object NewTemplateDefParents extends Parents(allowCommaSeparatedParentsInScala3 = false)
object TypeDefinitionParents extends Parents
