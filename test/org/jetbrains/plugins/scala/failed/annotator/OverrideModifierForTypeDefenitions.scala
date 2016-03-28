package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Created by kate on 3/28/16.
  */

//check in ScalaAnnotator with ModifierChecker.checkModifiers
class OverrideModifierForTypeDefenitions extends ScalaLightCodeInsightFixtureTestAdapter{
  def testSCL9700(): Unit = {
    checkTextHasNoErrors(
      """
        |trait T {
        |  val t: Any
        |}
        |
        |object U extends T {
        |  override object t
        |}
      """.stripMargin
    )
  }
}
