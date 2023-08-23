package org.jetbrains.plugins.scala.editor

import com.intellij.openapi.fileEditor.impl.{EditorComposite, EditorFileSwapper}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import kotlin.Pair
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

final class ScalaEditorFileSwapper extends EditorFileSwapper {
  override def getFileToSwapTo(project: Project, editorComposite: EditorComposite): Pair[VirtualFile, Integer] = {
    val sourceFile = ScalaEditorFileSwapper.findSourceFile(project, editorComposite.getFile)
    if (sourceFile ne null)
      new Pair(sourceFile, null)
    else
      null
  }
}

object ScalaEditorFileSwapper {
  private def findSourceFile(project: Project, eachFile: VirtualFile): VirtualFile =
    PsiManager.getInstance(project).findFile(eachFile) match {
      case file: ScalaFile if file.isCompiled =>
        val fqn: String = getFQN(file)
        if (fqn == null)
          return null

        var clazz: PsiClass = null
        for {
          cl <- ScalaPsiManager.instance(project).getCachedClasses(file.resolveScope, fqn)
          if clazz == null && cl.getContainingFile == file
        } clazz = cl

        if (!clazz.is[ScTypeDefinition])
          return null
        val sourceClass: PsiClass = clazz.asInstanceOf[ScTypeDefinition].getSourceMirrorClass
        if (sourceClass == null || (sourceClass eq clazz))
          return null

        val result: VirtualFile = sourceClass.getContainingFile.getVirtualFile
        assert(result != null)
        result
      case _ => null
    }

  private def getFQN(scalaFile: ScalaFile): String =
    scalaFile.typeDefinitions.headOption.map(_.qualifiedName).orNull
}