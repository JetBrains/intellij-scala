package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * [[InheritClauses]] ::= ['extends' [[ConstrApps]] ] ['derives' QualId {',' QualId}]
 */
object InheritClauses extends ParsingRule {

  // TODO derives
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean =
    ConstrApps()
}
