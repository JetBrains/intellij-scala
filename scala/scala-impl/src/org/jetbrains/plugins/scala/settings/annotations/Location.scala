package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.psi.{PsiClass, PsiElement, PsiModifier}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, Parent}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getModule
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
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
  def apply(anchor: PsiElement): Location = anchor match {
    case member: ScMember => new MemberAnchorLocation(member)
    case element => new ElementAnchorLocation(element)
  }

  def inside(aClass: PsiClass): Location =
    new InsideClassLocation(aClass)

  private class ElementAnchorLocation(element: PsiElement) extends Location {
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

    override def isInTestSources: Boolean = {
      val psiFile = Option(element.getContainingFile)
      val vFile = psiFile.flatMap(_.getVirtualFile.toOption)
      val index = ProjectFileIndex.SERVICE.getInstance(element.getProject)

      vFile.exists(index.isInTestSourceContent)
    }

    override def isInsideAnonymousClass: Boolean = false

    override def isInsidePrivateClass: Boolean = false

    override def isInsideOf(classes: Set[String]): Boolean = false
  }

  private class MemberAnchorLocation(member: ScMember) extends ElementAnchorLocation(member) {
    private val insideClassLocation = Option(member.getContainingClass).map(new InsideClassLocation(_))

    override def isInsideAnonymousClass: Boolean =
      insideClassLocation.exists(_.isInsideAnonymousClass)

    override def isInsidePrivateClass: Boolean =
      insideClassLocation.exists(_.isInsidePrivateClass)

    override def isInsideOf(classes: Set[String]): Boolean =
      insideClassLocation.exists(_.isInsideOf(classes))
  }

  private class InsideClassLocation(aClass: PsiClass) extends ElementAnchorLocation(aClass) {
    override def isInLocalScope: Boolean = false

    override def isInsideAnonymousClass: Boolean =
      aClass.isInstanceOf[ScNewTemplateDefinition]

    override def isInsidePrivateClass: Boolean = aClass match {
      case owner: ScModifierListOwner => owner.hasModifierPropertyScala(PsiModifier.PRIVATE)
      case _ => false
    }

    override def isInsideOf(classes: Set[String]): Boolean = Option(getModule(aClass)).map { module =>
      val scope = ElementScope(aClass.getProject, moduleWithDependenciesAndLibrariesScope(module))
      classes.flatMap(scope.getCachedClass).exists(it => aClass.equals(it) || aClass.isInheritor(it, true))
    } getOrElse {
      false
    }
  }
}
