package org.jetbrains.plugins.scala.lang.parser.parsing.top;

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.parser.parsing.top.ListOfStableIDs
/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 22:04:16
 */
class ImportList {

  def parse(builder: PsiBuilder): Unit = {

  Console.println("importList token: " + builder.getTokenType)

  while ( builder.getTokenType == ScalaTokenTypes.kIMPORT ) {

      val imMarker = builder.mark()
      builder.advanceLexer //New node: "import"
      imMarker.done( ScalaElementTypes.IMPORT )

      new Import() parse(builder)
      builder.advanceLexer()
    }

//Open marker for handle import


//node - IMPORT
    //builder.advanceLexer

    /*Console.println("token in import : " + builder.getTokenType)
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
    } */


  }
}
