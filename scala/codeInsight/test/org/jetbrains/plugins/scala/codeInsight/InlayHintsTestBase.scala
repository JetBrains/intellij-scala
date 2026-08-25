package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Inlay
import org.jetbrains.plugins.scala.base
import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.CollectionHasAsScala

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

  /** All the [[Text]] parts of all the inlays in `text`, in document order. */
  protected def inlayPartsIn(text: String): Seq[Text] = {
    configureFromFileText(text)
    myFixture.doHighlighting()

    val document = myFixture.getEditor.getDocument
    myFixture.getEditor.getInlayModel
      .getInlineElementsInRange(0, document.getTextLength)
      .asScala
      .toSeq
      .flatMap(inlay => Option(inlay.getRenderer).collect { case renderer: TextPartsHintRenderer => renderer })
      .flatMap(_.parts)
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
