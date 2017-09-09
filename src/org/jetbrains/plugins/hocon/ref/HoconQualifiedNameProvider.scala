package org.jetbrains.plugins.hocon.ref

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.editor.{Editor, EditorModificationUtil}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.hocon.psi.HKey

/**
  * @author ghik
  */
class HoconQualifiedNameProvider extends QualifiedNameProvider {
  def adjustElementToCopy(element: PsiElement): PsiElement = element

  def getQualifiedName(element: PsiElement): String = element match {
    case key: HKey => key.forParent(
      path => path.allKeys.map(_.iterator.map(_.getText).mkString(".")).orNull,
      field => field.keysInAllPaths.map(_.iterator.map(_.getText).mkString(".")).orNull
    )
    case _ => null
  }

  override def insertQualifiedName(fqn: String, element: PsiElement, editor: Editor, project: Project): Unit =
    EditorModificationUtil.insertStringAtCaret(editor, fqn)

  def qualifiedNameToElement(fqn: String, project: Project): PsiElement = null
}
