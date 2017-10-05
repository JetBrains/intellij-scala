package org.jetbrains.plugins.scala
package util

import com.intellij.formatting.FormattingModelBuilder
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.lang.ParserDefinition
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.findUsages.ScalaFindUsagesProvider
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder
import org.jetbrains.plugins.scala.lang.formatting._
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition
import org.jetbrains.plugins.scala.lang.surroundWith._
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors
/**
 * @author ilyas
 * Date: 09.10.2006
 *
 */
class ScalaToolsFactoryImpl extends ScalaToolsFactory {
  def createScalaParserDefinition: ParserDefinition = new ScalaParserDefinition()

  def createScalaFoldingBuilder: ScalaFoldingBuilder = new ScalaFoldingBuilder()

  def createSurroundDescriptors: SurroundDescriptors = new ScalaSurroundDescriptors()

  def createScalaFormattingModelBuilder: FormattingModelBuilder = new ScalaFormattingModelBuilder()

  def createStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder = null

  def createFindUsagesProvider = new ScalaFindUsagesProvider

}
