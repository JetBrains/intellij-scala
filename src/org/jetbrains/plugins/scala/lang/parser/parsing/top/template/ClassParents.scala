package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.template

import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Parents
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 *  TemplateParents ::= Constr {with AnnotType}
 */
object ClassParents extends ClassParents {
  override protected def constructor = Constructor

  override protected def parseParent(builder: ScalaPsiBuilder): Boolean =
    AnnotType.parse(builder, isPattern = false)
}

trait ClassParents extends Parents {
  protected def constructor: Constructor

  override protected def elementType = ScalaElementTypes.CLASS_PARENTS

  override protected def parseFirstParent(builder: ScalaPsiBuilder) = constructor.parse(builder)
}