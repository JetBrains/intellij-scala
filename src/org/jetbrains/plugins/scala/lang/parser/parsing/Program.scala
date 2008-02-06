package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.util._
import org.jetbrains.plugins.scala.util._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.ScalaBundle

import com.intellij.lang.PsiBuilder

/*
 * Program parses all content in scala source file
 */

class Program {

  def parse(builder: PsiBuilder): Unit = {

    // Debug print mode off
    DebugPrint.displayLog_=(false) 

    if ( !builder.eof() ){
      CompilationUnit.parse(builder)
    }

    if (!builder.eof()) {
      val trashMarker = builder.mark
      while (!builder.eof()) {
        builder error ScalaBundle.message("out.of.compilation.unit", new Array[Object](0))
        builder.advanceLexer
      }
      trashMarker.done(ScalaElementTypes.TRASH)
    }

  }
}