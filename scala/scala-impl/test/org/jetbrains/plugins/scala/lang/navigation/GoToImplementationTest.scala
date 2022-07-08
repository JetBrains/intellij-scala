package org.jetbrains.plugins.scala
package lang
package navigation

import com.intellij.codeInsight.navigation.GotoImplementationHandler
import org.junit.Assert.assertEquals

class GoToImplementationTest extends GoToTestBase {

  def testTraitImplementation(): Unit = {
    val fileText =
      """
        |trait a {
        |  def f<caret>
        |}
        |trait b extends a {
        |  def f = 1
        |}
        |case class c() extends b
        |case class d() extends b
      """.stripMargin
    configureFromFileText(fileText)

    val targetsCount = new GotoImplementationHandler()
      .getSourceAndTargetElements(getEditor, getFile)
      .targets
      .length
    assertEquals(s"Wrong number of targets: $targetsCount", 1, targetsCount)
  }
}
