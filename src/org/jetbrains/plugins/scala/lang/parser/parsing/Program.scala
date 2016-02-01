package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.util.DebugPrint

/*
 * Program parses all content in scala source file
 */
object Program extends Program {
  override protected val compilationUnit = CompilationUnit
}

trait Program {
  protected val compilationUnit: CompilationUnit

  def parse(builder: ScalaPsiBuilder): Int = {
    var parseState = 0
    // Debug print mode off
    DebugPrint.displayLog = false  

    if ( !builder.eof() ){
      parseState = compilationUnit.parse(builder)
    }

    if (!builder.eof()) {
      while (!builder.eof()) {
        builder error ErrMsg("out.of.compilation.unit")
        builder.advanceLexer()
      }
    }

    return parseState

  }
}