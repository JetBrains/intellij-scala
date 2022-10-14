package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.openapi.roots.TestSourcesFilter.isTestSources
import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.psi.{FileViewProvider, PsiClass, PsiCodeFragment, PsiElement, PsiModifier}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ViewProviderExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getModule
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScFileViewProvider, ScalaPsiUtil}

sealed trait Location {
  def isInLocalScope: Boolean
  def isInCodeFragment: Boolean
  def isInDialectSources: Boolean
  def isInTestSources: Boolean
  def isInsideAnonymousClass: Boolean
  def isInsidePrivateClass: Boolean
  def isInsideOf(classesFqn: collection.Set[String]): Boolean
}

object Location {

  def apply(anchor: PsiElement): Location = {
    val maybeMemberLocation = anchor match {
      case member: ScMember =>
        val containingClass = member.containingClass
        if (containingClass == null)
          None
        else
          Some(new MemberAnchorLocation(member, new InsideClassLocation(containingClass)))
      case _ => None
    }

    maybeMemberLocation.getOrElse(new DefaultLocation(anchor))
  }

  sealed class DefaultLocation(
    protected val element: PsiElement
  ) extends Location {
    override def isInLocalScope: Boolean = element match {
      case _: ScTemplateBody => false
      case _ =>
        val container = element.getParent
        !container.is[ScTemplateBody, PsiClass, ScalaFile, ScExtensionBody]
    }

    override final def isInCodeFragment: Boolean =
      element.getContainingFile.isInstanceOf[PsiCodeFragment]

    override final def isInDialectSources: Boolean =
      findScalaViewProvider.exists {
        case _: ScFileViewProvider => false
        case _ => true
      }

    override final def isInTestSources: Boolean =
      findScalaViewProvider
        .map(_.getVirtualFile)
        .exists(isTestSources(_, element.getProject))

    override def isInsideAnonymousClass: Boolean = false

    override def isInsidePrivateClass: Boolean = false

    override def isInsideOf(classesFqn: collection.Set[String]): Boolean = false

    private def containingFile = element.getContainingFile

    private def findScalaViewProvider: Option[FileViewProvider] = Option(containingFile)
      .map(_.getViewProvider)
      .filter(_.hasScalaPsi)
  }

  private final class MemberAnchorLocation(
    override protected val element: ScMember,
    delegate: InsideClassLocation
  ) extends DefaultLocation(element) {

    override def isInsideAnonymousClass: Boolean = delegate.isInsideAnonymousClass

    override def isInsidePrivateClass: Boolean = delegate.isInsidePrivateClass

    override def isInsideOf(classesFqn: collection.Set[String]): Boolean = delegate.isInsideOf(classesFqn)
  }

  final class InsideClassLocation(
    override protected val element: PsiClass
  ) extends DefaultLocation(element) {

    override def isInLocalScope: Boolean = false

    override def isInsideAnonymousClass: Boolean = element.isInstanceOf[ScNewTemplateDefinition]

    override def isInsidePrivateClass: Boolean = element match {
      case owner: ScModifierListOwner => owner.hasModifierPropertyScala(PsiModifier.PRIVATE)
      case _ => false
    }

    override def isInsideOf(classesFqn: collection.Set[String]): Boolean =
      Option(getModule(element))
        .map(moduleWithDependenciesAndLibrariesScope)
        .map(ElementScope(element.getProject, _))
        .exists(scope =>
          classesFqn
            .flatMap(scope.getCachedClass)
            .exists { clazz =>
              ScalaPsiUtil.thisSubsumes(element, clazz)
            }
        )
  }
}
