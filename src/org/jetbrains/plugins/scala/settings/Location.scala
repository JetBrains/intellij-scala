package org.jetbrains.plugins.scala.settings

import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getModule
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

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
  def apply(anchor: PsiElement): Location = new LocationImpl(anchor)

  private class LocationImpl(element: PsiElement) extends Location {
    override def isInLocalScope: Boolean = Some(element) match {
      case Some(_: ScTemplateBody | Parent(_: ScTemplateBody)) => false
      case Some(Parent(file: ScalaFile)) if !file.isScriptFile => false
      case Some(Parent(_: PsiClass)) => false
      case _ => true
    }

    override def isInScript: Boolean = element.getContainingFile match {
      case file: ScalaFile => file.isScriptFile || !file.getName.endsWith(".scala")
      case _ => false
    }

    override def isInTestSources: Boolean = false

    override def isInsideAnonymousClass: Boolean = false

    override def isInsidePrivateClass: Boolean = false

    override def isInsideOf(classes: Set[String]): Boolean = element match {
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
}

