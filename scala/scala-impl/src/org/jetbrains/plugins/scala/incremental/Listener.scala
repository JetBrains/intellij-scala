package org.jetbrains.plugins.scala
package incremental

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener, VisibleAreaEvent, VisibleAreaListener}
import com.intellij.openapi.editor.ex.{EditorEx, FoldingListener, FoldingModelEx, MarkupModelEx, RangeHighlighterEx}
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.{Editor, EditorFactory, FoldRegion}
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Disposer, Key}
import org.jetbrains.plugins.scala.incremental.Listener.INCREMENTAL_HIGHLIGHTING_KEY

import java.awt.event.{KeyAdapter, KeyEvent}

class Listener extends EditorFactoryListener {
  private val MaxDoubleKeyPressDuration = 500L * 1000000L // ns (0.5 s)

  private var updaters = Map.empty[Editor, Updater]

  private def markupModelListenerFor(editor: Editor) = new MarkupModelListener {
    override def afterAdded(highlighter: RangeHighlighterEx): Unit = {
      handleEvent(highlighter)
    }

    override def attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleChanged: Boolean): Unit = {
      handleEvent(highlighter)
    }

    private def handleEvent(highlighter: RangeHighlighterEx): Unit = {
      if (incremental.Highlighting.suppress) return

      if (highlighter.getErrorStripeTooltip == null) return

      val visibleRange = VisibleRange.exactIn(editor)
      if (visibleRange == null) return

      if (!highlighter.getTextRange.intersects(visibleRange)) {
        Updater.concealErrorStripeMark(highlighter, editor.getColorsScheme)
      }
    }
  }

  private val visibleAreaListener = new VisibleAreaListener {
    override def visibleAreaChanged(e: VisibleAreaEvent): Unit = {
      handleEvent(e.getEditor, delta = true)
    }
  }

  private val foldingListener = new FoldingListener {
    override def onFoldRegionStateChange(r: FoldRegion): Unit = {
      handleEvent(r.getEditor, delta = false)
    }
  }

  private def handleEvent(editor: Editor, delta: Boolean): Unit = {
    Highlighting.editor = editor
    updaters(editor).scheduleUpdate(delta)
  }

  private var previousKeyPressInstant = 0L

  private val keyListener = new KeyAdapter() {
    override def keyPressed(e: KeyEvent): Unit = {
      if (e.getKeyCode == KeyEvent.VK_ESCAPE) {
        if (System.nanoTime() - previousKeyPressInstant < MaxDoubleKeyPressDuration) {
          Highlighting.suppress = true
          DaemonCodeAnalyzer.getInstance(Highlighting.editor.getProject)
            .restart("Restart after pressing escape")
        }
        previousKeyPressInstant = System.nanoTime()
      }
    }
  }

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    val editor = event.getEditor

    if (!incremental.Highlighting.enabledIn(editor.getProject) || !isScalaIn(editor.virtualFile)) return

    if (editor.getUserData(INCREMENTAL_HIGHLIGHTING_KEY) != null) return
    connectTo(editor)
    editor.putUserData(INCREMENTAL_HIGHLIGHTING_KEY, "")
  }

  private def connectTo(editor: Editor): Unit = if (!updaters.contains(editor)) {
    val updater = new Updater(editor)
    updaters += editor -> updater
    Highlighting.editors = updaters.keySet.toList
    val markupModelListener = markupModelListenerFor(editor)
    editor.getMarkupModel.asInstanceOf[MarkupModelEx].addMarkupModelListener(updater, markupModelListener)
    editor.asInstanceOf[EditorEx].getFilteredDocumentMarkupModel.addMarkupModelListener(updater, markupModelListener)
    editor.getScrollingModel.addVisibleAreaListener(visibleAreaListener, updater)
    editor.getFoldingModel.asInstanceOf[FoldingModelEx].addListener(foldingListener, updater)
    editor.getContentComponent.addKeyListener(keyListener)
  }

  override def editorReleased(event: EditorFactoryEvent): Unit = {
    val editor = event.getEditor

    if (editor.getUserData(INCREMENTAL_HIGHLIGHTING_KEY) == null) return
    disconnectFrom(editor)
    editor.putUserData(INCREMENTAL_HIGHLIGHTING_KEY, null)
  }

  private def disconnectFrom(editor: Editor): Unit = {
    editor.getContentComponent.removeKeyListener(keyListener)
    Disposer.dispose(updaters(editor))
    updaters -= editor
    Highlighting.editors = updaters.keySet.toList
  }
}

private object Listener {
  private val INCREMENTAL_HIGHLIGHTING_KEY = Key.create[AnyRef]("incremental_highlighting_key")

  private def instance = new ExtensionPointName("com.intellij.editorFactoryListener").findExtensionOrFail(classOf[Listener])

  private def editorsIn(project: Project) = EditorFactory.getInstance.getAllEditors.filter(_.getProject == project)

  def connectTo(project: Project): Unit = editorsIn(project).foreach { editor =>
    instance.editorCreated(new EditorFactoryEvent(EditorFactory.getInstance, editor))
  }

  def disconnectFrom(project: Project): Unit = editorsIn(project).foreach { editor =>
    instance.editorReleased(new EditorFactoryEvent(EditorFactory.getInstance, editor))
  }
}
