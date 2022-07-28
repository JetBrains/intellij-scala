package org.jetbrains.plugins.scala.actions

import com.intellij.ide.actions.{JavaQualifiedNameProvider, QualifiedNameProvider}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}

class ScalaQualifiedNameProvider extends QualifiedNameProvider {

  override def adjustElementToCopy(element: PsiElement): PsiElement = null

  override def getQualifiedName(element: PsiElement): String = {
    element match {
      case clazz: ScTypeDefinition => clazz.qualifiedName
      case named: ScNamedElement =>
        val clazz = ScalaPsiUtil.nameContext(named) match {
          case member: ScMember => member.containingClass
          case _ => null
        }
        if (clazz != null) {
          clazz.qualifiedName + "#" + named.name
        } else {
          named.name
        }
      case _ => null
    }
  }

  override def qualifiedNameToElement(fqn: String, project: Project): PsiElement = {
    new JavaQualifiedNameProvider().qualifiedNameToElement(fqn, project) // TODO:
  }

  override def insertQualifiedName(fqn: String, element: PsiElement, editor: Editor, project: Project): Unit = {
    new JavaQualifiedNameProvider().insertQualifiedName(fqn, element, editor, project) // TODO
  }
}
