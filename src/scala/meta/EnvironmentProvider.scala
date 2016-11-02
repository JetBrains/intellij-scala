package scala.meta

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.{PsiFile, PsiManager}

trait EnvironmentProvider {
  def findFileByPath(path: String): PsiFile = {
    val virtualFile = VirtualFileManager.getInstance().findFileByUrl(path)
    PsiManager.getInstance(getCurrentProject).findFile(virtualFile)
  }
  def getCurrentProject: Project

  def dumbMode: Boolean = false
}

