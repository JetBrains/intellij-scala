package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.parser.parsing.top.ListOfStableIDs
import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
/**
 * User: Dmitry.Krasilschikov
 * Date: 10.10.2006
 * Time: 15:41:15
 */
class Import {
  def parse ( builder : PsiBuilder) : Unit = {

  builder.advanceLexer

  Console.println("    Import token: " + builder.getTokenType)
    builder.getTokenType match {

      case ScalaTokenTypes.tIDENTIFIER => {
         Console.println("  listMarker do ")
         val listImport = builder.mark();

         (new ListOfStableIDs).parse(builder)
         
         listImport.done(ScalaElementTypes.STABLE_ID_LIST) //new node: StableID list
         Console.println("  listMarker done ")

          builder.getTokenType match {
            case ScalaTokenTypes.tSEMICOLON => {
              val semiMarker = builder.mark()
              builder.advanceLexer
              semiMarker.done( ScalaTokenTypes.tSEMICOLON ) //new node: SEMICOLON
            }

            case _ => {}
          }

      }
              

      case _ => builder.error("Wrong import")
    }
  }
}