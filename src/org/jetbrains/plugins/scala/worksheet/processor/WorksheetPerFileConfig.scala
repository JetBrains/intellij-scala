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

  def isEnabled(file: PsiFile, attribute: FileAttribute) = FileAttributeUtilCache.readAttribute(attribute, file).contains("enabled")

  def setEnabled(file: PsiFile, attribute: FileAttribute, e: Boolean) {
    FileAttributeUtilCache.writeAttribute(attribute, file, if (e) enabled else disabled)
  }
}
