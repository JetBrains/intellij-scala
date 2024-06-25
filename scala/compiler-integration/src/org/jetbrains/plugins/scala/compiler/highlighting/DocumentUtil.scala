package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.util.DocumentVersion

private object DocumentUtil {
  def documentVersion(virtualFile: VirtualFile, document: Document): DocumentVersion =
    DocumentVersion(virtualFile.getCanonicalPath, version(document))

  def version(document: Document): Long = document match {
    case ex: DocumentEx => ex.getModificationSequence.toLong
    case doc => doc.getModificationStamp
  }
}
