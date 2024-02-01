package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.autoImport.GlobalMemberOwner
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging}

sealed trait ElementToImport {
  protected type E <: PsiNamedElement

  def element: E

  def qualifiedName: String

  final def presentation: String = s"<html><body>$presentationBody</body></html>"

  def presentationBody: String = qualifiedName

  final def name: String = element.name

  final def isValid: Boolean = element.isValid
}

object ElementToImport {
  @Nls
  def messageByType(toImport: Seq[ElementToImport])
                   (@Nls classes: String,
                    @Nls packages: String,
                    @Nls mixed: String): String =
    if (toImport.forall(_.element.isInstanceOf[PsiClass]))
      classes
    else if (toImport.forall(_.element.isInstanceOf[PsiPackage]))
      packages
    else
      mixed
}

final case class ClassToImport(override val element: PsiClass) extends ElementToImport {

  override protected type E = PsiClass

  override def qualifiedName: String = element.qualifiedName

  override def presentationBody: String = Presentation.withDeprecation(element)
}

final case class ExtensionMethodToImport(override val element: ScFunction,
                                         owner: GlobalMemberOwner,
                                         pathToOwner: String) extends ElementToImport {
  override protected type E = ScFunction

  override def qualifiedName: String = owner match {
    case GlobalMemberOwner.GivenDefinition(_) => pathToOwner
    case _ => pathToOwner + "." + name
  }

  override def presentationBody: String = owner match {
    case GlobalMemberOwner.GivenDefinition(_) => Presentation.withDeprecation(owner.element, pathToOwner)
    case _ => Presentation.withDeprecations(element, owner.element, pathToOwner)
  }
}

object ExtensionMethodToImport {
  def apply(element: ScFunction, owner: ScPackaging): ExtensionMethodToImport =
    new ExtensionMethodToImport(element, GlobalMemberOwner.Packaging(owner), owner.fqn)

  def apply(element: ScFunction, owner: PsiClass): ExtensionMethodToImport =
    new ExtensionMethodToImport(element, GlobalMemberOwner.Class(owner), owner.qualifiedName)
}

final case class MemberToImport(override val element: PsiNamedElement,
                                owner: GlobalMemberOwner,
                                pathToOwner: String) extends ElementToImport {

  override protected type E = PsiNamedElement

  override def qualifiedName: String = pathToOwner + "." + name

  override def presentationBody: String =
    Presentation.withDeprecations(element, owner.element, pathToOwner)
}

object MemberToImport {
  def apply(element: PsiNamedElement, owner: PsiClass): MemberToImport =
    MemberToImport(element, GlobalMemberOwner.Class(owner), owner.qualifiedName)

  def apply(element: PsiNamedElement, owner: ScPackaging): MemberToImport =
    MemberToImport(element, GlobalMemberOwner.Packaging(owner), owner.fqn)
}

final case class PrefixPackageToImport(override val element: ScPackage) extends ElementToImport {

  override protected type E = ScPackage

  override def qualifiedName: String = element.getQualifiedName
}

final case class ImplicitToImport(found: FoundImplicit) extends ElementToImport {
  protected type E = ScNamedElement

  override def element: ScNamedElement = found.instance.named

  override def qualifiedName: String = found.instance.qualifiedName

  override def presentationBody: String =
    Presentation.withDeprecations(element, found.instance.owner.element, found.instance.pathToOwner)
}
