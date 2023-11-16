package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx

@Service(Array(Service.Level.APP))
private final class RegisterFocusChangeListenerApplicationService extends Disposable {

  {
    // Relies on the fact that services are instantiated exactly once.
    val focusChangeListener = new CompilerHighlightingFocusChangeListener()
    EditorFactory.getInstance().getEventMulticaster.asInstanceOf[EditorEventMulticasterEx]
      .addFocusChangeListener(focusChangeListener, this)
  }

  override def dispose(): Unit = {}
}
