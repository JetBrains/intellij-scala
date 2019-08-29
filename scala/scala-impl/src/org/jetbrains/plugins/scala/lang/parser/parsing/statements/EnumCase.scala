package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.ClassConstr
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ConstrApps

/**
 * [[EnumCase]] ::= 'case' ( id [[ClassConstr]] [ 'extends' [[ConstrApps]] ] ] | [[Ids]])
 */
object EnumCase extends ParsingRule {

  import lexer.ScalaTokenTypes.{kCASE, tCOMMA, tIDENTIFIER}

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    val caseMarker = builder.mark()
    builder.getTokenType match {
      case `kCASE` =>
        builder.advanceLexer()
        builder.getTokenType match {
          case `tIDENTIFIER` =>
            if (builder.lookAhead(1) == tCOMMA) {
              Ids()
            } else {
              builder.advanceLexer()
              ClassConstr()
              ConstrApps()
            }

            caseMarker.done(ScalaElementType.ENUM_CASE_DEFINITION)
          case _ =>
            builder.error(ScalaBundle.message("identifier.expected"))
            caseMarker.drop()
        }

        true
      case _ =>
        caseMarker.rollbackTo()
        false
    }
  }
}
