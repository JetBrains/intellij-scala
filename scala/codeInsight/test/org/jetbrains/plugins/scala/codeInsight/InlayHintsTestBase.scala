package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Inlay
import org.jetbrains.plugins.scala.base
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer
import org.junit.experimental.categories.Category

@Category(Array(classOf[EditorTests]))
abstract class InlayHintsTestBase extends base.ScalaLightCodeInsightFixtureTestCase {

  override protected def setUp(): Unit = {
    super.setUp()
    scalaFixture.setFileTextPatcher { fileText =>
      s"""class Foo {
         |$fileText
         |}
         |
         |new Foo""".stripMargin
    }
  }

  protected def doInlayTest(text: String, withTooltips: Boolean = false): Unit = {
    configureFromFileText(text)
    val f = inlayText(withTooltips)
    myFixture.testInlays(f(_).get, f(_).isDefined)
  }

  /** Error tooltip messages of all inlays in `text`, in document order. */
  protected def inlayErrorTooltips(text: String): Seq[String] = {
    import scala.jdk.CollectionConverters._

    configureFromFileText(text)
    myFixture.doHighlighting()

    val editor = getEditor
    editor.getInlayModel
      .getInlineElementsInRange(0, editor.getDocument.getTextLength)
      .asScala
      .toSeq
      .map(_.getRenderer)
      .collect { case renderer: TextPartsHintRenderer => renderer }
      .flatMap(_.parts.flatMap(_.errorTooltip).map(_.message))
  }

  private def inlayText(withTooltips: Boolean): Inlay[_] => Option[String] = (_: Inlay[_]).getRenderer match {
    case renderer: TextPartsHintRenderer if withTooltips =>
      Some(renderer.parts.flatMap(p => p.string + p.tooltip().map(" /* " + _ + " */ ").mkString.replace("\"", "'").replace("\n", "\\n")).mkString)
    case renderer: HintRenderer => Some(renderer.getText)
    case _ => None
  }

  protected object Hint {
    val Start = """<hint text=""""
    val End = """"/>"""
  }
}
