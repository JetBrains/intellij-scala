package org.jetbrains.plugins.scala.lang.parser.parsing.top


import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser._;
/**
 * User: Dmitry.Krasilschikov
 * Date: 10.10.2006
 * Time: 15:41:15
 */
object Import {
  def parse ( builder : PsiBuilder) : Unit = {

  builder.advanceLexer


    builder.getTokenType match {

      case ScalaTokenTypes.tIDENTIFIER => {

         val listImport = builder.mark();

         (new ListOfStableIDs).parse(builder)
         
         listImport.done(ScalaElementTypes.STABLE_ID_LIST) //new node: StableID list

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