package org.jetbrains.plugins.scala
package annotator
package intention

import java.util.Collections

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs._
import com.intellij.psi.PsiFile

/** 
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

object QuickfixUtil {
  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    val virtualFile = file.getVirtualFile
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(Collections.singletonList(virtualFile))
    !operationStatus.hasReadonlyFiles
  }
}