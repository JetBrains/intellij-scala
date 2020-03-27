package org.jetbrains.plugins.scala

import com.intellij.openapi.vfs.VirtualFile

package object externalHighlighters {

  type HighlightingState = Map[VirtualFile, Set[ExternalHighlighting]]
}
