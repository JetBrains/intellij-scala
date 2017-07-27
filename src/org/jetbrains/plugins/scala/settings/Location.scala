package org.jetbrains.plugins.scala.settings

import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.psi.{PsiElement, PsiLocalVariable}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getModule
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScExpression, ScForStatement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

import scala.annotation.tailrec

/**
  * @author Pavel Fatin
  */
trait Location {
  def isInLocalScope: Boolean

  def isInScript: Boolean

  def isInTestSources: Boolean

  def isInsideAnonymousClass: Boolean

  def isInsidePrivateClass: Boolean

  def isInsideOf(classes: Set[String]): Boolean
}

object Location {
  def apply(anchor: PsiElement): Location = new AnchorLocation(anchor)

  private class AnchorLocation(anchor: PsiElement) extends Location {
    override def isInLocalScope: Boolean = isLocal(anchor)

    override def isInScript: Boolean = false

    override def isInTestSources: Boolean = false

    override def isInsideAnonymousClass: Boolean = false

    override def isInsidePrivateClass: Boolean = false

    override def isInsideOf(classes: Set[String]): Boolean = anchor match {
      case member: ScMember => isMemberOf(member, classes)
      case _ => false
    }
  }

  private def isMemberOf(member: ScMember, classes: Set[String]): Boolean = {
    Option(member.getContainingClass).flatMap { containingClass =>
      Option(getModule(member)).map { module =>
        val scope = ElementScope(member.getProject, moduleWithDependenciesAndLibrariesScope(module))
        classes.flatMap(scope.getCachedClass).exists(containingClass.isInheritor(_, true))
      }
    } getOrElse {
      false
    }
  }

  // TODO simplify
  @tailrec
  private final def isLocal(psiElement: PsiElement): Boolean = psiElement match {
    case null => false
    case expression: ScExpression => !expression.getParent.isInstanceOf[ScTemplateBody]
    case member: ScMember => member.isLocal
    case _: ScEnumerator | _: ScForStatement | _: PsiLocalVariable => true
    case _: ScTemplateBody => false
    case _ => isLocal(psiElement.getContext)
  }
}

