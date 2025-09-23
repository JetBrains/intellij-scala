package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.{ApiStatus, TestOnly}

/**
 * This class was introduced for tests primarily.
 * Thus with `@TestOnly` we are not pretending that it's supposed to be some best solution
 */
@TestOnly
@ApiStatus.Experimental
trait ExternalHighlightingAppliedListener {
  def highlightingApplied(virtualFiles: Set[VirtualFile]): Unit
}

object ExternalHighlightingAppliedListener {
  final val topic = Topic.create("ExternalHighlightingAppliedListener", classOf[ExternalHighlightingAppliedListener])
}