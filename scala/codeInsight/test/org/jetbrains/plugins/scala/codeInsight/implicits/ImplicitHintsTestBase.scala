package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.{Inlay => IdeaInlay}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

abstract class ImplicitHintsTestBase extends ScalaLightCodeInsightFixtureTestAdapter{
  override protected final def configureFromFileText(fileText: String) =
    super.configureFromFileText(
      s"""class Foo {
         |$fileText
         |}
         |
         |new Foo""".stripMargin
    )

  protected def doTest(text: String): Unit = {

    val oldEnabled = ImplicitHints.enabled
    try {
      ImplicitHints.enabled = true
      configureFromFileText(text)
      getFixture.testInlays(
        inlayText(_).get,
        inlayText(_).isDefined
      )
    } finally {
      ImplicitHints.enabled = oldEnabled
    }
  }

  private val inlayText = (_: IdeaInlay[_]).getRenderer match {
    case renderer: HintRenderer => Some(renderer.getText)
    case _ => None
  }
}

private[implicits] object ImplicitHintsTestBase {

  val HintStart = "<hint text=\""
  val HintEnd = "\"/>"
}