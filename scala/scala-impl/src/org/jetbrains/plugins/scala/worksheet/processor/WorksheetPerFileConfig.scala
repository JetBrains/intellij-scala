package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.PsiFile

/**
 * User: Dmitry.Naydanov
 * Date: 30.07.14.
 */
trait WorksheetPerFileConfig {
  protected val enabled = "enabled"
  protected val disabled = "disable"
  
  protected def getStringRepresent(e: Boolean): String = if (e) enabled else disabled

  protected def isEnabled(file: PsiFile, attribute: FileAttribute): Boolean =
    FileAttributeUtilCache.readAttribute(attribute, file).contains(enabled)

  protected def isDisabled(file: PsiFile, attribute: FileAttribute): Boolean =
    FileAttributeUtilCache.readAttribute(attribute, file).contains(disabled)

  protected def setEnabled(file: PsiFile, attribute: FileAttribute, e: Boolean) {
    FileAttributeUtilCache.writeAttribute(attribute, file, getStringRepresent(e))
  }
}
