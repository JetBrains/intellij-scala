package  org.jetbrains.plugins.scala.lang.parser.parsing.top;

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.top.StableId
/**
 * User: Dmitry.Krasilschikov
 * Date: 09.10.2006
 * Time: 18:44:14
 */
class ListOfStableIDs {

 def parse(builder : PsiBuilder) : Unit = {

    Console.println("     ListOfStableIDs token: " + builder.getTokenType)

    //val marker = builder.mark()
    //builder.advanceLexer

    while ( ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType) ){

        Console.println("  idMarker do ")
        val idMarker = builder.mark()
        builder.advanceLexer  //have to be tDOT or tCOMMA, or tEND_OF_LINE, or COLON

        (new StableId).parse( builder, idMarker )

        Console.println("      stableID parsed")
       // idMarker.drop() //marker to null
        Console.println("  idMarker drop ")

        Console.println("      token after StableID " + builder.getTokenType)


         builder.getTokenType match {

            case ScalaTokenTypes.tCOMMA => {

              Console.println("      token after StableID" + builder.getTokenType)

              val commaMarker = builder.mark()

              commaMarker.done( ScalaTokenTypes.tCOMMA ) //new node: COMMA

              builder.advanceLexer


              //val idMarker = builder.mark()

              //(new StableId).parse(builder, idMarker)
              //idMarker.drop()

              if ( !ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType) ){
                builder.error("expected idetifier")
              }
            }


            case _ => {}
          }
    }
  }
}