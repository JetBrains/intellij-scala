package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top.template

import org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Parents
import org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScClassParentsElementType

/**
* @author Alexander Podkhalyuzin
* Date: 08.02.2008
*/

/*
 *  TemplateParents ::= Constr {with AnnotType}
 */
object ClassParents extends Parents {

  override protected def elementType: ScClassParentsElementType = ScalaElementTypes.CLASS_PARENTS

  override protected def parseFirstParent(builder: ScalaPsiBuilder): Boolean = Constructor.parse(builder)

  override protected def parseParent(builder: ScalaPsiBuilder): Boolean = AnnotType.parse(builder, isPattern = false)
}