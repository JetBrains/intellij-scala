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
  override protected val constructor = Constructor
  override protected val annotType = AnnotType
}

trait ClassParents extends Parents {
  protected val constructor: Constructor

  override protected val elementType = ScalaElementTypes.CLASS_PARENTS

  override protected def parseParent(builder: ScalaPsiBuilder) = constructor.parse(builder)
}