package org.jetbrains.plugins.scala.annotator.intention

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** 
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

object QuickfixUtil {
  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    //todo:
    return true
  }
}