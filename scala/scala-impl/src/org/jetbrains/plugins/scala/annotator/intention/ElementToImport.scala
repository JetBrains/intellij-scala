package org.jetbrains.plugins.scala
package annotator
package intention

import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.quickfix.FoundImplicit
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

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

final case class MemberToImport(override val element: PsiNamedElement,
                                owner: PsiClass) extends ElementToImport {

  override protected type E = PsiNamedElement

  override def qualifiedName: String = owner match {
    case ClassQualifiedName(qualifiedName) if qualifiedName.nonEmpty =>
      qualifiedName + "." + name
    case _ =>
      name
  }

  override def presentationBody: String =
    Presentation.withDeprecations(element, owner)
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
    Presentation.withDeprecations(element, found.instance.containingObject)
}