package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 *   Date: 29.02.2008
 */

/*
 * Pattern2 ::= varid '@' Pattern3
 *            | _ '@' Pattern3
 *            | Pattern3
 */
object Pattern2 extends Pattern2 {
  override protected val pattern3 = Pattern3
}

trait Pattern2 {
  protected val pattern3: Pattern3

  def parse(builder: ScalaPsiBuilder, forDef: Boolean): Boolean = {

    def isVarId = {
      val text = builder.getTokenText
      text.substring(0, 1).toLowerCase != text.substring(0, 1) || 
              text.apply(0) == '`' && text.apply(text.length - 1) == '`'
    }

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
          pattern2Marker.done(ScalaElementTypes.REFERENCE_PATTERN)
          return true
        } else if (isVarId) {
          backupMarker.rollbackTo()
        } else {
          builder.advanceLexer() //Ate id
          val idMarker = builder.mark
          builder.getTokenType match {
            case ScalaTokenTypes.tAT =>
              builder.advanceLexer() //Ate @
              backupMarker.drop()
              if (!pattern3.parse(builder)) {
                idMarker.rollbackTo()
                pattern2Marker.done(ScalaElementTypes.REFERENCE_PATTERN)
                val err = builder.mark
                builder.advanceLexer()
                err.error(ErrMsg("wrong.pattern"))
              } else {
                idMarker.drop()
                pattern2Marker.done(ScalaElementTypes.NAMING_PATTERN)
              }
              return true
            case _ =>
              idMarker.drop()
              backupMarker.rollbackTo()
          }
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
              pattern2Marker.done(ScalaElementTypes.REFERENCE_PATTERN)
              val err = builder.mark
              builder.advanceLexer()
              err.error(ErrMsg("wrong.pattern"))
            } else {
              idMarker.drop()
              pattern2Marker.done(ScalaElementTypes.NAMING_PATTERN)
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