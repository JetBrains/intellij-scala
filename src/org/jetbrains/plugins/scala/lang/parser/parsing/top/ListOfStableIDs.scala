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

 def parse(builder : PsiBuilder, listMarker : PsiBuilder.Marker) : Unit = {

    Console.print("ListOfStableIDs token: " + builder.getTokenType)
    val marker = builder.mark()
    builder.advanceLexer
    marker.done(ScalaElementTypes.STABLE_ID_LIST)

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val idMarker = builder.mark()
        (new StableId).parse( builder, idMarker )
        idMarker.drop()
      }

      case ScalaTokenTypes.tCOMMA => {
        builder.mark().done( ScalaTokenTypes.tCOMMA )
        val marker = builder.mark()
        builder.advanceLexer
        (new StableId).parse(builder, marker)
      }

    }

  }
}
