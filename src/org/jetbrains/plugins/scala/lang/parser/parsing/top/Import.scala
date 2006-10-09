package org.jetbrains.plugins.scala.lang.parser.parsing.top;

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder
/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 22:04:16
 */
class Import {

  def parse(builder: PsiBuilder): Unit = {

//Open marker for handle import
    var marker = builder.mark()
    

//node - IMPORT
    builder.advanceLexer

    Console.println("token in import : " + builder.getTokenType)
    builder.getTokenType match {

      case ScalaTokenTypes.tCOMMA => {
        builder.advanceLexer
        new Import parse(builder)
      }
    }

    builder.getTokenType match {
      
      //handle full class name
      case ScalaTokenTypes.tIDENTIFIER => new StableId parse(builder)

      case _ => builder.error("Wrong import")
    }

    marker.done(ScalaElementTypes.IMPORT) //Close marker for import
  }
}
