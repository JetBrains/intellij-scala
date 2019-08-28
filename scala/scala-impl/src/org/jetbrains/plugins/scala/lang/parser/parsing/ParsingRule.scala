package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

trait ParsingRule {

  def parse()(implicit builder: ScalaPsiBuilder): Boolean

  // TODO: after all parsing rule implement this interface remove these methods
  @deprecated
  final def parse(builder: ScalaPsiBuilder)(implicit d: DummyImplicit): Boolean = parse()(builder)
  final def apply()(implicit builder: ScalaPsiBuilder): Boolean = parse()
}
