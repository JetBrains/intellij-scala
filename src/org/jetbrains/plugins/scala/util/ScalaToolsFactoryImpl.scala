package org.jetbrains.plugins.scala.util

import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder

/**
 * Author: Ilya Sergey
 * Date: 09.10.2006
 * Time: 21:26:04
 */
class ScalaToolsFactoryImpl extends ScalaToolsFactory {

  def createScalaParserDefinition: ParserDefinition = {
    new ScalaParserDefinition()
  }

  def createScalaFoldingBuilder: ScalaFoldingBuilder = {
    new ScalaFoldingBuilder()
  }

}
