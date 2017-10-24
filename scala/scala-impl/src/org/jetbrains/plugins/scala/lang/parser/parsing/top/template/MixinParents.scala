package org.jetbrains.plugins.scala.lang.parser.parsing.top.template

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Parents
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementTypes}

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * MixinParents ::= AnnotType {'with' AnnotType}
 */
object MixinParents extends MixinParents {
  override protected def annotType = AnnotType
}

trait MixinParents extends Parents {
  protected def annotType: AnnotType

  override protected def elementType = ScalaElementTypes.TRAIT_PARENTS

  override protected def parseFirstParent(builder: ScalaPsiBuilder) = {
    val result = annotType.parse(builder, isPattern = false)
    if (!result) {
      builder.error(ErrMsg("wrong.simple.type"))
    }
    result
  }

  override protected def parseParent(builder: ScalaPsiBuilder): Boolean =
    parseFirstParent(builder)
}
