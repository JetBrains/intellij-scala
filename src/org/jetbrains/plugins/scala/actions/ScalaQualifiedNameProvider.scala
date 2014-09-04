package org.jetbrains.plugins.scala.actions

import com.intellij.ide.actions.{JavaQualifiedNameProvider, QualifiedNameProvider}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}

/**
 * @author Alefas
 * @since 25.06.12
 */

class ScalaQualifiedNameProvider extends QualifiedNameProvider {
  def adjustElementToCopy(element: PsiElement) = null

  def getQualifiedName(element: PsiElement): String = {
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

  def qualifiedNameToElement(fqn: String, project: Project) = {
    new JavaQualifiedNameProvider().qualifiedNameToElement(fqn, project) //todo:
  }

  def insertQualifiedName(fqn: String, element: PsiElement, editor: Editor, project: Project) {
    new JavaQualifiedNameProvider().insertQualifiedName(fqn, element, editor, project) //todo:
  }
}
