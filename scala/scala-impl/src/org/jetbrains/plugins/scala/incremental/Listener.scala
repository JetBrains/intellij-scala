package org.jetbrains.plugins.scala
package incremental

import project.ProjectExt

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener, VisibleAreaEvent, VisibleAreaListener}
import com.intellij.openapi.editor.ex.{FoldingListener, FoldingModelEx}
import com.intellij.openapi.editor.{Editor, EditorFactory, FoldRegion}
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

import java.awt.event.{KeyAdapter, KeyEvent}

class Listener extends EditorFactoryListener {
  private val MaxDoubleKeyPressDuration = 500L * 1000000L // ns (0.5 s)

  private var updaters = Map.empty[Editor, Updater]

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
          DaemonCodeAnalyzer.getInstance(Highlighting.editor.getProject).restart()
        }
        previousKeyPressInstant = System.nanoTime()
      }
    }
  }

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    val editor = event.getEditor

    if (!incremental.Highlighting.enabledIn(editor.getProject) || !isScalaIn(editor.getVirtualFile)) return

    connectTo(editor)
  }

  private def connectTo(editor: Editor): Unit = if (!updaters.contains(editor)) {
    val updater = new Updater(editor)
    updaters += editor -> updater
    editor.getScrollingModel.addVisibleAreaListener(visibleAreaListener, updater)
    editor.getFoldingModel.asInstanceOf[FoldingModelEx].addListener(foldingListener, updater)
    editor.getContentComponent.addKeyListener(keyListener)
  }

  override def editorReleased(event: EditorFactoryEvent): Unit = {
    val editor = event.getEditor

    if (!incremental.Highlighting.enabledIn(editor.getProject) || !isScalaIn(editor.getVirtualFile)) return

    disconnectFrom(editor)
  }

  private def disconnectFrom(editor: Editor): Unit = if (updaters.contains(editor)) {
    editor.getContentComponent.removeKeyListener(keyListener)
    Disposer.dispose(updaters(editor))
    updaters -= editor
  }
}

private object Listener {
  private def instance = new ExtensionPointName("com.intellij.editorFactoryListener").findExtensionOrFail(classOf[Listener])

  private def editors = EditorFactory.getInstance.getAllEditors.filter(editor => isScalaIn(editor.getVirtualFile))

  def connectTo(project: Project): Unit = {
    editors.foreach(instance.connectTo)
  }

  def disconnectFrom(project: Project): Unit = {
    editors.foreach(instance.disconnectFrom)
  }
}
