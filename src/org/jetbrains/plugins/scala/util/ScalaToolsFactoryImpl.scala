package org.jetbrains.plugins.scala.util

import com.intellij.lang.ParserDefinition
import com.intellij.lang.folding.FoldingBuilder
import org.jetbrains.plugins.scala.lang.surroundWith.SurroundDescriptors

import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors

/**
 * Author: Ilya Sergey
 * Date: 09.10.2006
 * Time: 21:26:04
 */

class ScalaToolsFactoryImpl extends ScalaToolsFactory {

  def createScalaParserDefinition: ParserDefinition = new ScalaParserDefinition()

  def createScalaFoldingBuilder: FoldingBuilder = new ScalaFoldingBuilder()

  def createSurroundDescriptors : SurroundDescriptors = new ScalaSurroundDescriptors()
}
