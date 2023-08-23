package org.jetbrains.plugins.scala.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.sbt.Sbt

package object notification {

  private[notification]
  def isScalaSourceFile(file: VirtualFile, project: Project): Boolean =
    if (file.isWritable) {
      val psiFile = PsiManager.getInstance(project).findFile(file)
      if (psiFile != null) {
        psiFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE) &&
          !psiFile.getName.endsWith(Sbt.Extension) // root sbt files belong to main (not *-build) modules
      }
      else false
    }
    else false
}
