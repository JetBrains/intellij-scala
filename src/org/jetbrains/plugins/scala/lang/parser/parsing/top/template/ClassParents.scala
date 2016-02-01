package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.template

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 *  TemplateParents ::= Constr {with AnnotType}
 */
object ClassParents extends ClassParents {
  override protected val constructor = Constructor
  override protected val annotType = AnnotType
}

trait ClassParents {
  protected val constructor: Constructor
  protected val annotType: AnnotType

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val classParentsMarker = builder.mark
    if (!constructor.parse(builder)) {
      classParentsMarker.drop()
      return false
    }
    //Look for mixin
    while (builder.getTokenType == ScalaTokenTypes.kWITH) {
      builder.advanceLexer() //Ate with
      if (!annotType.parse(builder, isPattern = false)) {
        builder error ScalaBundle.message("wrong.simple.type")
        classParentsMarker.done(ScalaElementTypes.CLASS_PARENTS)
        return true
      }
    }
    classParentsMarker.done(ScalaElementTypes.CLASS_PARENTS)
    true
  }
}