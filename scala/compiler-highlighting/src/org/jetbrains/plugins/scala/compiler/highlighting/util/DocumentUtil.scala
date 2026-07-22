package org.jetbrains.plugins.scala.compiler.highlighting.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.SerializableMap
import org.jetbrains.plugins.scala.util.{CanonicalPath, DocumentVersion}

import java.nio.file.Path

private[highlighting] object DocumentUtil {
  def documentVersion(virtualFile: VirtualFile, document: Document): DocumentVersion =
    DocumentVersion(CanonicalPath(virtualFile.getCanonicalPath), version(document))

  def version(document: Document): Long = document match {
    case ex: DocumentEx => ex.getModificationSequence.toLong
    case doc => doc.getModificationStamp
  }

  def stillValid(documentVersions: SerializableMap[CanonicalPath, Long]): Boolean =
    documentVersions.forall { case (CanonicalPath(path), version) =>
      val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(path))
      if (virtualFile eq null) false
      else {
        val document = FileDocumentManager.getInstance().getCachedDocument(virtualFile)
        if (document eq null) false else version == DocumentUtil.version(document)
      }
    }
}
