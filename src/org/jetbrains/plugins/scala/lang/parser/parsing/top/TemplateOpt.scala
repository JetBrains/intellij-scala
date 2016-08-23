package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
trait TemplateOpt {
  def parse(builder: ScalaPsiBuilder): Unit
}
