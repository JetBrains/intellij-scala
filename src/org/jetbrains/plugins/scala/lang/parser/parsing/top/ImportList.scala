package org.jetbrains.plugins.scala.lang.parser.parsing.top;

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder

import org.jetbrains.plugins.scala.lang.parser.parsing.top.ListOfStableIDs
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Top
/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 22:04:16
 */
class ImportList {
/*
  def parse( builder: PsiBuilder ): Unit = {

    def getNumberOfImport : Int = {
      var count : Int = 0;
      val top = new Top()

      top.skipLineTerminators(builder)
      while (! builder.eof())  {
        if (ScalaTokenTypes.kIMPORT.equals(builder.getTokenType)){
          count = count + 1
        }
         builder.advanceLexer
      }
      count
    }

  //counting number of import statements
    val initImportMarker = builder.mark()
    val num = getNumberOfImport
    initImportMarker.rollbackTo()

   // val importListMarker = builder.mark()

    var i = 1;
    while ( ScalaTokenTypes.kIMPORT.equals(builder.getTokenType) && (i <= num ) ) {
      val imStMarker = builder.mark()

      val importMarker = builder.mark()
      builder.advanceLexer //New node: "import"

      importMarker.done( ScalaElementTypes.IMPORT )

      Import.parse(builder)

      imStMarker.done( ScalaElementTypes.IMPORT_STMT )

      if (i != num ) {
        new Top() skipLineTerminators( builder )
      }

      i = i + 1
    }

   //importListMarker.done(ScalaElementTypes.IMPORT_LIST)
  }*/
}
