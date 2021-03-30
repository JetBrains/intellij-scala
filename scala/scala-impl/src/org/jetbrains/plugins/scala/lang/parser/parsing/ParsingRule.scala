package org.jetbrains.plugins.scala
package lang
package parser
package parsing

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

trait ParsingRule {

  // TODO: swap parse and apply methods:
  //  apply should be final and it's should be treated as a syntax sugar for calling parse method
  //  if you use method explicitly, there is no sense in calling apply `rule.apply`, use `rule.parse`
  //  Currently `GoTo` parse method redirects to this base method, not to the actual implementation.
  //  There wouldn't same prolem with `apply` method, because it wouldn't be called directly,
  //  so you could `GoTo` only to the rule definition itself
  //
  final def parse(implicit builder: ScalaPsiBuilder): Boolean = apply()

  def apply()(implicit builder: ScalaPsiBuilder): Boolean
}

object ParsingRule {

  object AlwaysTrue extends ParsingRule {
    override def apply()(implicit builder: ScalaPsiBuilder): Boolean = true
  }
}