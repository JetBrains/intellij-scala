package org.jetbrains.plugins.scala
package lang.navigation

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.codeInsight.navigation.GotoImplementationHandler

/**
 * @author Alefas
 * @since 24.12.13
 */
class GoToImplementationTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def testTraitImplementation() {
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
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)

    val targets = new GotoImplementationHandler().getSourceAndTargetElements(getEditorAdapter, getFileAdapter).targets
    assert(targets.length == 1, s"Wrong number of targets: ${targets.length}")
  }
}
