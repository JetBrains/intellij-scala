package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package params

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ConstrMods
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses

/**
 * [[ClassConstr]] ::= [ [[TypeParamClause]] //ClsTypeParamClause] [ [[ConstrMods]] ] [[ClassParamClauses]]
 */
object ClassConstr extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    TypeParamClause.parse(builder)

    val constructorMarker = builder.mark()
    ConstrMods()
    ClassParamClauses()
    constructorMarker.done(ScalaElementType.PRIMARY_CONSTRUCTOR)

    true
  }
}
