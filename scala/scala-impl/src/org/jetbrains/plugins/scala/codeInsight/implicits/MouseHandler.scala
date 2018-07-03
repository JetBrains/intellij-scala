package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.Point

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event._
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager

class MouseHandler(project: Project,
                   startupManager: StartupManager,
                   editorFactory: EditorFactory) extends AbstractProjectComponent(project) {

  private val mousePressListener = new EditorMouseAdapter {
    override def mousePressed(e: EditorMouseEvent): Unit = {
      MouseHandler.mousePressLocation = e.getMouseEvent.getPoint
    }
  }

  startupManager.registerPostStartupActivity(() => {
    val multicaster = editorFactory.getEventMulticaster
    multicaster.addEditorMouseListener(mousePressListener, project)
  })
}

object MouseHandler {
  var mousePressLocation: Point = new Point(0, 0)
}
