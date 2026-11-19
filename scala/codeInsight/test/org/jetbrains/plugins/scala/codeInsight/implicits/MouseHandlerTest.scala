package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{EditorMouseEvent, EditorMouseEventArea, EditorMouseListener, EditorMouseMotionListener}
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.scala.annotator.hints.Hint.MenuProvider
import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.junit.Assert.assertEquals

import java.awt.Cursor
import java.awt.event.{InputEvent, MouseEvent}
import scala.jdk.CollectionConverters._

class MouseHandlerTest extends ScalaLightCodeInsightFixtureTestCase {
  def testHoverInScalaFile(): Unit = checkHover("Example.scala")

  def testHoverInPlayTemplate(): Unit = checkHover("example.scala.html")

  private def checkHover(fileName: String): Unit = {
    myFixture.configureByText(fileName, "val x: Int = true")
    val editor = getEditor
    val renderer = new TextPartsHintRenderer(
      Seq(Text("...", expansion = Some(() => Seq(Text("Boolean"))))), MenuProvider.NoMenu
    )
    val inlay = editor.getInlayModel.addInlineElement(editor.getDocument.getTextLength, renderer)
    val bounds = inlay.getBounds
    val point = new java.awt.Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)
    val component = editor.getContentComponent
    component.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))

    val multicaster = EditorFactory.getInstance().getEventMulticaster.asInstanceOf[EditorEventMulticasterImpl]
    val oldListeners = multicaster.getListeners
    new MouseHandler().execute(getProject)
    val listeners = multicaster.getListeners
    val mouseListeners = listeners.get(classOf[EditorMouseListener]).asScala
      .filterNot(oldListeners.get(classOf[EditorMouseListener]).contains(_)).map(_.asInstanceOf[EditorMouseListener])
    val motionListeners = listeners.get(classOf[EditorMouseMotionListener]).asScala
      .filterNot(oldListeners.get(classOf[EditorMouseMotionListener]).contains(_)).map(_.asInstanceOf[EditorMouseMotionListener])
    try {
      val move = new MouseEvent(component, MouseEvent.MOUSE_MOVED, 0, InputEvent.CTRL_DOWN_MASK,
        point.x, point.y, 0, 0, 0, false, MouseEvent.NOBUTTON)
      val event = new EditorMouseEvent(editor, move, EditorMouseEventArea.EDITING_AREA)
      motionListeners.foreach(_.mouseMoved(event))
      PlatformTestUtil.waitWithEventsDispatching(
        "Hovering over the expandable inlay should change the cursor",
        () => component.getCursor.getType == Cursor.HAND_CURSOR,
        2
      )

      val click = new MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, 0,
        point.x, point.y, 0, 0, 1, false, MouseEvent.BUTTON1)
      mouseListeners.foreach(_.mouseClicked(new EditorMouseEvent(editor, click, EditorMouseEventArea.EDITING_AREA)))
      assertEquals("Boolean", renderer.getText)
    } finally {
      motionListeners.foreach(multicaster.removeEditorMouseMotionListener)
      mouseListeners.foreach(multicaster.removeEditorMouseListener)
      inlay.dispose()
    }
  }
}
