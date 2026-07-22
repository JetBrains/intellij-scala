package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly

/**
 *
 *
 * This interface is used to notify listeners that the highlighting markup has been applied on the editor.
 * It should only be used in tests.
 *
 * @note Listeners run on a background thread; they must not assume the UI thread or hold the read/write lock.
 */
@TestOnly
private[highlighting] trait ExternalHighlightingAppliedListener {
  /**
   * Callback function invoked when the highlighting markup has been applied on the editor.
   * @param virtualFiles the files on which the highlighting markup has been applied
   */
  def highlightingApplied(virtualFiles: Set[VirtualFile]): Unit
}

private[highlighting] object ExternalHighlightingAppliedListener {
  val topic: Topic[ExternalHighlightingAppliedListener] = new Topic(
    "compiler highlighting applied",
    classOf[ExternalHighlightingAppliedListener]
  )
}
