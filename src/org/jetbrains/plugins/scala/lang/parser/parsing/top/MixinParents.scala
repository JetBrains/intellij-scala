package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * MixinParents ::= AnnotType {'with' AnnotType}
 */
object MixinParents extends MixinParents {
  override protected val annotType = AnnotType
}

trait MixinParents extends Parents {
  override protected val elementType = ScalaElementTypes.TRAIT_PARENTS

  override protected def parseParent(builder: ScalaPsiBuilder) = {
    val result = annotType.parse(builder, isPattern = false)
    if (!result) {
      builder.error(ErrMsg("wrong.simple.type"))
    }
    result
  }
}