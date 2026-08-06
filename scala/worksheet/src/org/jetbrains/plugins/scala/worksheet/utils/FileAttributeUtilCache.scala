package org.jetbrains.plugins.scala.worksheet.utils

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}

import scala.collection.mutable

object FileAttributeUtilCache {

  private val lightKeys = mutable.WeakHashMap[VirtualFile, mutable.HashMap[FileAttribute, String]]()

  private def getAttribute(file: VirtualFile, attribute: FileAttribute) =
    Option(attribute readAttributeBytes file) map (new String(_))

  def readAttribute(attribute: FileAttribute, file: VirtualFile): Option[String] =
    file match {
      case normalFile: VirtualFileWithId =>
        getAttribute(normalFile, attribute)
      case other =>
        lightKeys.get(other).flatMap(_.get(attribute))
    }

  def writeAttribute(attribute: FileAttribute, file: VirtualFile, data: String): Unit =
    file match {
      case normalFile: VirtualFileWithId =>
        attribute.writeAttributeBytes(normalFile, data.getBytes)
      case other =>
        lightKeys.get(other) match {
          case Some(e) =>
            e.put(attribute, data)
          case _ =>
            lightKeys.put(other, mutable.HashMap(attribute -> data))
        }
    }
}
