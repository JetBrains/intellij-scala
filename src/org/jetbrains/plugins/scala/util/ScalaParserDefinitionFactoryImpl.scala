package org.jetbrains.plugins.scala.util

import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition

/**
 * Author: Ilya Sergey
 * Date: 09.10.2006
 * Time: 21:26:04
 */
class ScalaParserDefinitionFactoryImpl extends ScalaParserDefinitionFactory {
  def createScalaParserDefinition(): ParserDefinition = {
    new ScalaParserDefinition()
  }
}
