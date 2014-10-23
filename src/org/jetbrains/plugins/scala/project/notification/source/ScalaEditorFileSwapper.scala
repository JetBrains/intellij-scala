package org.jetbrains.plugins.scala
package project.notification.source

import com.intellij.openapi.fileEditor.impl.{EditorFileSwapper, EditorWithProviderComposite}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

object ScalaEditorFileSwapper {
  def findSourceFile(project: Project, eachFile: VirtualFile): VirtualFile = {
    val psiFile: PsiFile = PsiManager.getInstance(project).findFile(eachFile)
    psiFile match {
      case file: ScalaFile if file.isCompiled =>
      case _ => return null
    }
    val fqn: String = getFQN(psiFile)
    if (fqn == null) return null
    val classes = ScalaPsiManager.instance(project).getCachedClasses(psiFile.getResolveScope, fqn)
    var clazz: PsiClass = null
    for (cl <- classes if clazz == null) {
      if (cl.getContainingFile == psiFile) clazz = cl
    }
    if (!clazz.isInstanceOf[ScTypeDefinition]) return null
    val sourceClass: PsiClass = clazz.asInstanceOf[ScTypeDefinition].getSourceMirrorClass
    if (sourceClass == null || (sourceClass eq clazz)) return null
    val result: VirtualFile = sourceClass.getContainingFile.getVirtualFile
    assert(result != null)
    result
  }

  def getFQN(psiFile: PsiFile): String = {
    if (!psiFile.isInstanceOf[ScalaFile]) return null
    val classes = psiFile.asInstanceOf[ScalaFile].typeDefinitions
    if (classes.length == 0) return null
    val fqn: String = classes(0).qualifiedName
    if (fqn == null) return null
    fqn
  }
}

class ScalaEditorFileSwapper extends EditorFileSwapper {
  def getFileToSwapTo(project: Project,
                      editorWithProviderComposite: EditorWithProviderComposite): Pair[VirtualFile, Integer] = {
    Pair.create(ScalaEditorFileSwapper.findSourceFile(project, editorWithProviderComposite.getFile), null)
  }
}