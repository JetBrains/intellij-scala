package org.jetbrains.plugins.scala.worksheet

import com.intellij.lang.Language
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

final class WorksheetFile(viewProvider: FileViewProvider, language: Language with WorksheetLanguageLike)
  extends ScalaFileImpl(viewProvider, WorksheetFileType, language) {

  override def toString: String = "WorksheetFile: " + getName

  override def isWorksheetFile = true

  def isRepl: Boolean = {
    // isRepl can be used in completion, so extracting original virtualFile for in-memory psi file
    val vFile = ScFile.VirtualFile.unapply(this)
    vFile.fold(false) { file =>
      WorksheetFileSettings(getProject, file).isRepl
    }
  }

  override def isMultipleDeclarationsAllowed: Boolean = isRepl
}