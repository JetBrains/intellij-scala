package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile

import scala.collection.mutable

/**
 * User: Dmitry.Naydanov
 * Date: 30.07.14.
 */
object FileAttributeUtilCache {
  private val lightKeys = mutable.WeakHashMap[VirtualFile, mutable.HashMap[FileAttribute, String]]()
  
  private def getAttribute(file: VirtualFile, attribute: FileAttribute) = 
    Option(attribute readAttributeBytes file) map (new String(_))

  def readAttribute(attribute: FileAttribute, file: PsiFile): Option[String] = {
    file.getVirtualFile match {
      case normalFile: VirtualFileWithId => getAttribute(normalFile, attribute)
      case other => lightKeys get other flatMap (map => map get attribute)
    }
  }
  
  //We need this method as sometimes (e.g. during completion) normal virtual file is "hidden" beneath light file
  def readAttributeLight(attribute: FileAttribute, file: PsiFile): Option[String] = {
    readAttribute(attribute, file).orElse {
      file.getViewProvider.getVirtualFile match {
        case light: LightVirtualFile => Option(light.getOriginalFile) flatMap (normalFile => getAttribute(normalFile, attribute))
        case _ => None
      }
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
