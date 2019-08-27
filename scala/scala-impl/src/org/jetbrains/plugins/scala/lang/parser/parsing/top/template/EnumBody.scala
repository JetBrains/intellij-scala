package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top
package template

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SelfType

/**
 * [[EnumBody]] ::= [nl] '{' [ [[SelfType]] ] [[EnumStat]] { semi [[EnumStat]] } '}'
 */
object EnumBody extends TemplateBody {

  //noinspection TypeAnnotation
  override protected def elementType = ScalaElementType.ENUM_BODY

  override protected def parseStatement()(implicit builder: ScalaPsiBuilder): Boolean =
    EnumStat()
}
