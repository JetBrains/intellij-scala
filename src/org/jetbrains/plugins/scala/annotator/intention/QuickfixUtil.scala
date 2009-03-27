package org.jetbrains.plugins.scala.annotator.intention

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.openapi.vfs._

/** 
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

object QuickfixUtil {
  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    val virtualFile = file.getVirtualFile()
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(virtualFile)
    return !operationStatus.hasReadonlyFiles()
  }
}