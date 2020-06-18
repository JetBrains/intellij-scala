package org.jetbrains.plugins.scala
package annotator
package intention

import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.quickfix.FoundImplicit
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ContainingClass, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}

sealed trait ElementToImport {
  protected type E <: PsiNamedElement

  def element: E

  def qualifiedName: String

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
}

final case class MethodToImport(override val element: PsiMethod) extends MemberToImport {

  override protected type E = PsiMethod
}

final case class DefinitionToImport(override val element: ScTypedDefinition) extends ElementToImport {

  override protected type E = ScTypedDefinition

  override def qualifiedName: String = element match {
    case ContainingClass(ClassQualifiedName(qualifiedName)) if qualifiedName.nonEmpty =>
      qualifiedName + "." + name
    case _ =>
      name
  }
}

final case class TypeAliasToImport(override val element: ScTypeAlias) extends MemberToImport {

  override protected type E = ScTypeAlias
}

sealed abstract class MemberToImport extends ElementToImport {

  override protected type E <: PsiMember with PsiNamedElement

  override final def qualifiedName: String = element match {
    case ContainingClass(ClassQualifiedName(qualifiedName)) if qualifiedName.nonEmpty =>
      qualifiedName + "." + name
    case _ =>
      name
  }
}

final case class PrefixPackageToImport(override val element: ScPackage) extends ElementToImport {

  override protected type E = ScPackage

  override def qualifiedName: String = element.getQualifiedName
}

final case class ImplicitToImport(found: FoundImplicit) extends ElementToImport {
  protected type E = ScNamedElement

  override def element: ScNamedElement = found.instance.named

  override def qualifiedName: String = found.instance.qualifiedName
}