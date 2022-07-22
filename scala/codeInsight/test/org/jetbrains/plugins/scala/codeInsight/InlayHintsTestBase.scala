package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Inlay
import org.jetbrains.plugins.scala.base
import org.junit.experimental.categories.Category

@Category(Array(classOf[EditorTests]))
abstract class InlayHintsTestBase extends base.ScalaLightCodeInsightFixtureTestAdapter {

  override protected final def configureFromFileText(fileText: String) =
    super.configureFromFileText(
      s"""class Foo {
         |$fileText
         |}
         |
         |new Foo""".stripMargin
    )

  protected def doInlayTest(text: String): Unit = {
    configureFromFileText(text)
    myFixture.testInlays(
      inlayText(_).get,
      inlayText(_).isDefined
    )
  }

  private val inlayText = (_: Inlay[_]).getRenderer match {
    case renderer: HintRenderer => Some(renderer.getText)
    case _ => None
  }

  protected object Hint {

    val Start = "<hint text=\""
    val End = "\"/>"
  }
}
