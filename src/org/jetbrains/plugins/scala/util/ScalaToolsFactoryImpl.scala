package org.jetbrains.plugins.scala
package util

import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors
import org.jetbrains.plugins.scala.lang.surroundWith._
import org.jetbrains.plugins.scala.lang.formatting._
import org.jetbrains.plugins.scala.lang.findUsages.ScalaFindUsagesProvider
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.psi.PsiFile
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
