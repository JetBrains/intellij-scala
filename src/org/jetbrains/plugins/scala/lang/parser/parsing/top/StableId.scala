package org.jetbrains.plugins.scala.lang.parser.parsing.top;

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.lang.parser.parsing.top.ListOfStableIDs
/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 22:13:17
 */
class StableId {

  def parse(builder : PsiBuilder, marker : PsiBuilder.Marker ) : Unit = {


    Console.println("token in stableID : " + builder.getTokenType)
     builder.getTokenType match {

     case ScalaTokenTypes.tDOT => {

       val marker = builder.mark()
       builder.advanceLexer

       val preMarker = marker.precede()
       marker.done(ScalaElementTypes.STABLE_ID)

       val dotMarker = builder.mark();
       builder.advanceLexer
       dotMarker.done(ScalaElementTypes.DOT)

       builder.getTokenType match {
         case ScalaTokenTypes.tIDENTIFIER => {

           val idMarker = builder.mark()
           builder.advanceLexer
           idMarker.done(ScalaElementTypes.IDENTIFIER)

           (new StableId).parse(builder, preMarker)
         }
          case _ => builder.error("Wrong import name")

       }
     }

      case ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE => {
        //marker.done(ScalaElementTypes.STABLE_ID)
        builder.advanceLexer
      }

      case ScalaTokenTypes.tSEMICOLON => {
        //marker.done(ScalaElementTypes.STABLE_ID)
        val marker = builder.mark()
        builder.advanceLexer
        marker.done(ScalaElementTypes.SEMICOLON)
      }

      case ScalaTokenTypes.tCOMMA => {

        (new ListOfStableIDs).parse (builder, builder.mark())
      }

      case _ => builder.error("Wrong import name")
    }


    }

  }

