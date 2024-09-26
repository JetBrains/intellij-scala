package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import org.jetbrains.plugins.scala.util.{CanonicalPath, CompilationId, DocumentVersion}

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

private object DocumentUtil {
  def documentVersion(virtualFile: VirtualFile, document: Document): DocumentVersion =
    DocumentVersion(CanonicalPath(virtualFile.getCanonicalPath), version(document))

  def version(document: Document): Long = document match {
    case ex: DocumentEx => ex.getModificationSequence.toLong
    case doc => doc.getModificationStamp
  }

  def stillValid(documentVersions: Map[CanonicalPath, Long] with Serializable): Boolean =
    documentVersions.forall { case (CanonicalPath(path), version) =>
      val url = URLDecoder.decode(Path.of(path).toUri.toString, StandardCharsets.UTF_8)
      val virtualFile = VirtualFileManager.getInstance().findFileByUrl(url)
      if (virtualFile eq null) false
      else {
        val document = FileDocumentManager.getInstance().getCachedDocument(virtualFile)
        if (document eq null) false else version == DocumentUtil.version(document)
      }
    }
}
