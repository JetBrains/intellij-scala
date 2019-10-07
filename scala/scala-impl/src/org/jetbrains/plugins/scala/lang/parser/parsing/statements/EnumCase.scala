package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Ids, Modifier}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations
import org.jetbrains.plugins.scala.lang.parser.parsing.params.ClassConstr
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ConstrApps

/**
 * [[EnumCase]] ::= 'case' ( id [[ClassConstr]] [ 'extends' [[ConstrApps]] ] | [[Ids]] )
 */
object EnumCase extends ParsingRule {

  import ScalaElementType.{EnumCase => SingleCase, _}
  import lexer.ScalaTokenTypes._

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()

    Annotations()

    val modifierMarker = builder.mark()
    while (Modifier.parse(builder)) {}
    modifierMarker.done(MODIFIERS)

    builder.getTokenType match {
      case `kCASE` =>
        builder.advanceLexer()

        builder.getTokenType match {
          case `tIDENTIFIER` =>
            val targetElementType = builder.lookAhead(1) match {
              case `tCOMMA` =>
                Ids()

                ScalaElementType.EnumCases
              case _ =>
                builder.advanceLexer()

                ClassConstr()
                parseParents()

                SingleCase
            }

            marker.done(targetElementType)
            true
          case _ =>
            marker.drop()
            false
        }
      case _ =>
        marker.drop()
        false
    }
  }

  private def parseParents()(implicit builder: ScalaPsiBuilder): Unit = {
    val marker = builder.mark()

    builder.getTokenType match {
      case `kEXTENDS` =>
        builder.advanceLexer()
        ConstrApps()
      case _ =>
    }

    marker.done(ScalaElementType.EXTENDS_BLOCK)
  }
}
