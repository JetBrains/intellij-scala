package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * Scala 2.x
 * Pattern2      ::= varid '@' Pattern3
 *                 | _ '@' Pattern3
 *                 | Pattern3
 *
 * Scala 3
 * Pattern2      ::=  [id ‘@’] InfixPattern
 * InfixPattern  ::=  SimplePattern { id [nl] SimplePattern }
 */
object Pattern2 {
  protected def pattern3: Pattern3.type = Pattern3

  def parse(builder: ScalaPsiBuilder, forDef: Boolean): Boolean = {
    def testForId = {
      val m = builder.mark
      builder.advanceLexer()
      val s = Set(ScalaTokenTypes.tAT,
        ScalaTokenTypes.tIDENTIFIER,
        ScalaTokenTypes.tDOT,
        ScalaTokenTypes.tLPARENTHESIS)
      val b = !s.contains(builder.getTokenType)
      m.rollbackTo()
      b
    }

    val pattern2Marker = builder.mark
    val backupMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        if (forDef && testForId) {
          backupMarker.drop()
          builder.advanceLexer()
          pattern2Marker.done(ScalaElementType.REFERENCE_PATTERN)
          return true
        } else if (builder.isIdBinding) {
          builder.advanceLexer() //Ate id
          val idMarker = builder.mark
          builder.getTokenType match {
            case ScalaTokenTypes.tAT =>
              builder.advanceLexer() //Ate @
              backupMarker.drop()
              if (!pattern3.parse(builder)) {
                idMarker.rollbackTo()
                pattern2Marker.done(ScalaElementType.REFERENCE_PATTERN)
                val err = builder.mark
                builder.advanceLexer()
                err.error(ErrMsg("wrong.pattern"))
              } else {
                idMarker.drop()
                pattern2Marker.done(ScalaElementType.NAMING_PATTERN)
              }
              return true
            case _ =>
              idMarker.drop()
              backupMarker.rollbackTo()
          }
        } else {
          backupMarker.rollbackTo()
        }
      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate id
        val idMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.tAT =>
            builder.advanceLexer() //Ate @
            backupMarker.drop()
            if (!pattern3.parse(builder)) {
              idMarker.rollbackTo()
              pattern2Marker.done(ScalaElementType.REFERENCE_PATTERN)
              val err = builder.mark
              builder.advanceLexer()
              err.error(ErrMsg("wrong.pattern"))
            } else {
              idMarker.drop()
              pattern2Marker.done(ScalaElementType.NAMING_PATTERN)
            }
            return true
          case _ =>
            idMarker.drop()
            backupMarker.rollbackTo()
        }
      case _ =>
        backupMarker.drop()
    }
    pattern2Marker.drop()
    pattern3.parse(builder)
  }
}