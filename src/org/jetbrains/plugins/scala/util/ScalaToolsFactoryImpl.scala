package org.jetbrains.plugins.scala.util

import com.intellij.lang.ParserDefinition
import com.intellij.psi.FileViewProvider
import com.intellij.codeInsight.completion.CompletionData
import org.jetbrains.plugins.scala.lang.psi.javaView.ScJavaFileImpl
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionData
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors
import org.jetbrains.plugins.scala.lang.surroundWith._

/**
 * Author: Ilya Sergey
 * Date: 09.10.2006
 * Time: 21:26:04
 */

class ScalaToolsFactoryImpl extends ScalaToolsFactory {

  def createScalaParserDefinition: ParserDefinition = new ScalaParserDefinition()

  def createScalaFoldingBuilder: ScalaFoldingBuilder = new ScalaFoldingBuilder()

  def createJavaView (viewProvider : FileViewProvider) = new ScJavaFileImpl(viewProvider)

  def createScalaCompletionData : CompletionData = new ScalaCompletionData()

  def createSurroundDescriptors : CompletionData = new ScalaCompletionData()
}
