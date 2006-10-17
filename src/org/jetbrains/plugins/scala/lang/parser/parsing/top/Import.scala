package org.jetbrains.plugins.scala.lang.parser.parsing.top


import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.base


/**
 * User: Dmitry.Krasilschikov
 * Date: 10.10.2006
 * Time: 15:41:15
 */
object Import {
  def parse ( builder : PsiBuilder) : Unit = {

  ParserUtils.eatElement(builder, ScalaElementTypes.IMPORT)


    builder.getTokenType match {

      case ScalaTokenTypes.tIDENTIFIER => {

         val listImport = builder.mark();

         Console.println("ListOfStableIDs handle")
         ListOfStableIDs.parse(builder)
         Console.println("ListOfStableIDs handled")
         
         listImport.done(ScalaElementTypes.STABLE_ID_LIST) //new node: StableID list

          Console.println("token type : " + builder.getTokenType)
          builder.getTokenType match {

            case ScalaTokenTypes.tSEMICOLON => {
              val semiMarker = builder.mark()
              builder.advanceLexer
              semiMarker.done( ScalaTokenTypes.tSEMICOLON ) //new node: SEMICOLON
            }

            case _ => {}
          }
          //StatementSeparator.parse(builder)
      }
      case _ => builder.error("Wrong import")
    }
  }
}