package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.params.ClassConstr
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ConstrApps

/**
 * [[EnumCase]] ::= 'case' (id [[ClassConstr]] [ 'extends' [[ConstrApps]] ] ] | ids)
 */
object EnumCase extends ParsingRule {

  import ScalaElementType._
  import lexer.ScalaTokenTypes.{kCASE, tIDENTIFIER}

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val caseMarker = builder.mark()
    builder.getTokenType match {
      case `kCASE` =>
        builder.advanceLexer()
        builder.getTokenType match {
          case `tIDENTIFIER` =>
            builder.advanceLexer()
            ClassConstr()
            ConstrApps()
          case _ =>
            builder.error(ScalaBundle.message("identifier.expected"))
        }

        caseMarker.done(ENUM_CASE_DEFINITION)
        true
      case _ =>
        caseMarker.rollbackTo()
        false
    }
  }
}
