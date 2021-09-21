package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
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
abstract class Pattern2(forDef: Boolean) extends ParsingRule {
  private val patternInForLookAhead = Set(
    ScalaTokenTypes.tAT,
    ScalaTokenTypes.tIDENTIFIER,
    ScalaTokenTypes.tDOT,
    ScalaTokenTypes.tLPARENTHESIS,
  )

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    def testForId =
      !patternInForLookAhead.contains(builder.lookAhead(1))

    val pattern2Marker = builder.mark()
    val backupMarker = builder.mark()
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER =>
        if (forDef && testForId) {
          backupMarker.drop()
          builder.advanceLexer()
          pattern2Marker.done(ScalaElementType.REFERENCE_PATTERN)
          return true
        } else if (builder.isIdBinding) {
          builder.advanceLexer() //Ate id
          val idMarker = builder.mark()
          if (builder.getTokenType == ScalaTokenTypes.tAT) {
            builder.advanceLexer() // ate @
            backupMarker.drop()
            if (!Pattern3()) {
              idMarker.rollbackTo()
              pattern2Marker.done(ScalaElementType.REFERENCE_PATTERN)
              val err = builder.mark()
              builder.advanceLexer()
              err.error(ErrMsg("wrong.pattern"))
            } else {
              idMarker.drop()
              pattern2Marker.done(ScalaElementType.NAMING_PATTERN)
            }
            return true
          } else {
            idMarker.drop()
            backupMarker.rollbackTo()
          }
        } else {
          backupMarker.rollbackTo()
        }
      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate id
        val idMarker = builder.mark()
        builder.getTokenType match {
          case ScalaTokenTypes.tAT =>
            builder.advanceLexer() //Ate @
            backupMarker.drop()
            if (!Pattern3()) {
              idMarker.rollbackTo()
              pattern2Marker.done(ScalaElementType.REFERENCE_PATTERN)
              val err = builder.mark()
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
    Pattern3()
  }
}

object Pattern2 extends Pattern2(forDef = false)

object Pattern2InForDef extends Pattern2(forDef = true)