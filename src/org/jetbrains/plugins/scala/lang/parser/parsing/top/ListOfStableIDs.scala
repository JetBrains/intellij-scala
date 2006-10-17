package  org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
/**
 * User: Dmitry.Krasilschikov
 * Date: 09.10.2006
 * Time: 18:44:14
 */
object ListOfStableIDs {

 def parse(builder : PsiBuilder) : Unit = {

    while ( ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType) ){

       // val idMarker = builder.mark()
       // builder.advanceLexer  //have to be tDOT or tCOMMA, or tEND_OF_LINE, or COLON

        //StableId.parse( builder, idMarker )
        Console.println("StableId handle")
        StableId.parse( builder )
        Console.println("StableId handled")

         Console.println("token type : " + builder.getTokenType)
         builder.getTokenType() match {
            case ScalaTokenTypes.tCOMMA => {

              val commaMarker = builder.mark()
              commaMarker.done( ScalaTokenTypes.tCOMMA ) //new node: COMMA

              builder.advanceLexer

              if ( !ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType) ){
                builder.error("expected idetifier")
              }
            }

            case _ => {}
          }
    }
  }
}