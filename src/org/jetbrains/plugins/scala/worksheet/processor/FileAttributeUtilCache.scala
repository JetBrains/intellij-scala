package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.openapi.vfs.{VirtualFileWithId, VirtualFile}
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.PsiFile

import scala.collection.mutable

/**
 * User: Dmitry.Naydanov
 * Date: 30.07.14.
 */
object FileAttributeUtilCache {
  private val lightKeys = mutable.WeakHashMap[VirtualFile, mutable.HashMap[FileAttribute, String]]()

  def readAttribute(attribute: FileAttribute, file: PsiFile): Option[String] = {
    file.getVirtualFile match {
      case normalFile: VirtualFileWithId => Option(attribute readAttributeBytes normalFile) map (new String(_))
      case other => lightKeys get other flatMap (map => map get attribute)
    }
  }

  def writeAttribute(attribute: FileAttribute, file: PsiFile, data: String) {
    file.getVirtualFile match {
      case normalFile: VirtualFileWithId => attribute.writeAttributeBytes(normalFile, data.getBytes)
      case other => lightKeys get other match {
        case Some(e) => e.put(attribute, data)
        case _ => lightKeys.put(other, mutable.HashMap(attribute -> data))
      }
    }
  }
}
