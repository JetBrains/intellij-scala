package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.10.2006
 * Time: 18:08:23
 */

class Top extends ScalaTokenTypes{

  def skipLineTerminators (builder : PsiBuilder) : Unit = {
    while ( (!builder.eof()) && ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE.equals(builder.getTokenType)){
      Console.println("skipping one line terminator")
      builder.advanceLexer
    }
  }

  def parse(builder: PsiBuilder): Unit = {

  if (builder.getTokenType.equals(ScalaTokenTypes.kPACKAGE)) {
      new Package().parse(builder)

      skipLineTerminators(builder)
  }

  if (builder.getTokenType.equals(ScalaTokenTypes.kIMPORT)) {
    
      //Console println("in import : " + builder.getTokenType)
      Console.println("handling import list do ")
      new ImportList().parse(builder)
      Console.println("handling import list done ")



    //  builder.advanceLexer()

  }

   /* if ( builder.getTokenType == ScalaTokenTypes.kPACKAGE) {
      new Package().parse(builder)
      builder.advanceLexer()
    }

//handle IMPORT LIST
    if ( builder.getTokenType == ScalaTokenTypes.kIMPORT) {
      val importListMarker = builder.mark()

      new ImportList().parse(builder)
      
      importListMarker.done( ScalaElementTypes.IMPORT_LIST )
      builder.advanceLexer()
    }    
     */
  }
}