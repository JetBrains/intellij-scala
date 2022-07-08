package org.jetbrains.plugins.scala
package settings
package annotations

import com.intellij.openapi.roots.TestSourcesFilter.isTestSources
import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.psi.{PsiClass, PsiCodeFragment, PsiElement, PsiModifier}
import org.jetbrains.plugins.scala.extensions.ViewProviderExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getModule
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScFileViewProvider, ScalaPsiUtil}

sealed abstract class Location(protected val element: PsiElement) {

  def isInLocalScope: Boolean = element match {
    case _: ScTemplateBody => false
    case _ =>
      element.getParent match {
        case _: ScTemplateBody |
             _: PsiClass => false
        case file: ScalaFile if !file.isScriptFile => false
        case _ => true
      }
  }

  final def isInCodeFragment: Boolean =
    element.getContainingFile.isInstanceOf[PsiCodeFragment]

  final def isInDialectSources: Boolean =
    findScalaViewProvider.exists {
      case _: ScFileViewProvider => false
      case _ => true
    }

  final def isInTestSources: Boolean =
    findScalaViewProvider
      .map(_.getVirtualFile)
      .exists(isTestSources(_, element.getProject))

  def isInsideAnonymousClass: Boolean = false

  def isInsidePrivateClass: Boolean = false

  def isInsideOf(classes: Set[String]): Boolean = false

  private def containingFile = element.getContainingFile

  private def findScalaViewProvider = Option(containingFile)
    .map(_.getViewProvider)
    .filter(_.hasScalaPsi)
}

object Location {

  def apply(anchor: PsiElement): Location = {
    val maybeMemberLocation = anchor match {
      case member: ScMember => member.getContainingClass match {
        case null => None
        case containingClass => Some(new MemberAnchorLocation(member, InsideClassLocation(containingClass)))
      }
      case _ => None
    }

    maybeMemberLocation.getOrElse(new Location(anchor) {})
  }

  private final class MemberAnchorLocation(override protected val element: ScMember,
                                           delegate: InsideClassLocation) extends Location(element) {

    override def isInsideAnonymousClass: Boolean = delegate.isInsideAnonymousClass

    override def isInsidePrivateClass: Boolean = delegate.isInsidePrivateClass

    override def isInsideOf(classes: Set[String]): Boolean = delegate.isInsideOf(classes)
  }

  final class InsideClassLocation(override protected val element: PsiClass) extends Location(element) {

    override def isInLocalScope: Boolean = false

    override def isInsideAnonymousClass: Boolean = element.isInstanceOf[ScNewTemplateDefinition]

    override def isInsidePrivateClass: Boolean = element match {
      case owner: ScModifierListOwner => owner.hasModifierPropertyScala(PsiModifier.PRIVATE)
      case _ => false
    }

    override def isInsideOf(classes: Set[String]): Boolean =
      Option(getModule(element))
        .map(moduleWithDependenciesAndLibrariesScope)
        .map(ElementScope(element.getProject, _))
        .exists(scope =>
          classes
            .flatMap(scope.getCachedClass)
            .exists { clazz =>
              ScalaPsiUtil.thisSubsumes(element, clazz)
            }
        )
  }

  object InsideClassLocation {

    def apply(`class`: PsiClass) = new InsideClassLocation(`class`)
  }
}
