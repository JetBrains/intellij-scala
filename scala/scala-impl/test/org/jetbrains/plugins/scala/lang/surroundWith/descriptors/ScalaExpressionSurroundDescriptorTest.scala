package org.jetbrains.plugins.scala.lang.surroundWith.descriptors

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals

class ScalaExpressionSurroundDescriptorTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testMultipleLineComments(): Unit = {
    configureFromFileText(
      s"""$START//comment 1
         |//comment 2$END
         |object DbSchema:
         |  transparent inline def foo[EC <: Product, E <: Product, ID](p: String) = $${ ??? }
         |""".stripMargin
    )

    val selection = getEditor.getSelectionModel

    val surrounder = new ScalaExpressionSurroundDescriptor()
    val result = surrounder.getElementsToSurround(
      getFile,
      selection.getSelectionStart,
      selection.getSelectionEnd
    )

    assertEquals(
      Seq("//comment 2"),
      result.map(_.getText).toSeq
    )
  }
}