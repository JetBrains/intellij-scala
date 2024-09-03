package org.jetbrains.plugins.scala.actions

import com.intellij.ide.actions.{JavaQualifiedNameProvider, QualifiedNameProvider}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}

/**
 * See also [[org.jetbrains.plugins.scala.extensions.PsiMemberExt.qualifiedNameOpt]]
 * (Q: What is the difference? Shouldn't they be unified and one use another?)
 */
class ScalaQualifiedNameProvider extends QualifiedNameProvider {

  override def adjustElementToCopy(element: PsiElement): PsiElement = null

  override def getQualifiedName(element: PsiElement): String = {
    element match {
      case clazz: ScTypeDefinition => clazz.qualifiedName
      case named: ScNamedElement =>
        val nameContext = named.nameContext
        val containingClass = nameContext match {
          case member: ScMember => member.containingClass
          case _ => null
        }

        val name = named.name
        if (containingClass != null) {
          val separator = containingClass match {
            case _: ScObject => "."
            case _ => "#"
          }
          containingClass.qualifiedName + separator + name
        } else {
          val topLevelQualifier = nameContext match {
            case member: ScMember => //handle scala 3 top-level definitions
              member.topLevelQualifier
            case _ =>
              None
          }

          topLevelQualifier
            .filterNot(_.isEmpty) //ScMember.topLevelQualifier returns for a file without a package
            .fold(name)(_ + "." + name)
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
