package org.jetbrains.plugins.scala.compiler.highlighting.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.SerializableMap
import org.jetbrains.plugins.scala.util.{CanonicalPath, DocumentVersion}

import java.nio.file.Path
import scala.collection.immutable

private[highlighting] object DocumentUtil {
  def documentVersion(virtualFile: VirtualFile, document: Document): DocumentVersion =
    DocumentVersion(canonicalPath(virtualFile), version(document))

  /**
   * Identifies the text `document` holds: its modification stamp. Loading a document gives it the file's stamp and
   * saving gives the file the document's, so equal stamps mean the same text.
   */
  def version(document: Document): Long = document.getModificationStamp

  /** The current text of `virtualFile`: its loaded document's [[version]], otherwise the file's stamp. */
  def textStamp(virtualFile: VirtualFile): Long =
    Option(FileDocumentManager.getInstance().getCachedDocument(virtualFile))
      .fold(virtualFile.getModificationStamp)(version)

  def canonicalPath(virtualFile: VirtualFile): CanonicalPath = CanonicalPath(virtualFile.getCanonicalPath)

  /**
   * The stamp of what is on the disk for each of `files`. This is the file's stamp, not the document's.
   */
  def diskVersions(files: Iterable[VirtualFile]): SerializableMap[CanonicalPath, Long] =
    files.iterator
      .collect {
        case file if file.getCanonicalFile ne null => canonicalPath(file) -> file.getModificationStamp
      }
      .to(immutable.HashMap.mapFactory[CanonicalPath, Long])

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
