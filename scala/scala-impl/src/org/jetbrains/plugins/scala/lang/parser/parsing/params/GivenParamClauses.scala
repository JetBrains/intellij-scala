package org.jetbrains.plugins.scala.lang.parser.parsing.params

import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

object GivenParamClauses extends ParsingRule {
  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    var hadGiven = false
    while (GivenParamClause.parse(hadGiven)) {
      hadGiven = true
    }
    true
  }
}
