package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.vfs.VirtualFile

/**
 * The snapshot of a compilation as it was dispatched, handed back when it completes so that its outcome is
 * recorded against the state the compiler actually saw. 
 */
final case class CompilationToken private[compilation](epoch: Long, files: Set[VirtualFile])
