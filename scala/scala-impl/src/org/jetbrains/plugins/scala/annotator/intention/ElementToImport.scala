package org.jetbrains.plugins.scala.annotator.intention

import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

sealed trait ElementToImport {
  protected type E <: PsiNamedElement

  def element: E

  def name: String = element.name

  def qualifiedName: String

  def isAnnotationType: Boolean = false

  def isValid: Boolean = element.isValid
}

object ElementToImport {

  def messageByType(toImport: Array[ElementToImport])(classes: String, packages: String, mixed: String): String = {
    val toImportSeq = toImport.toSeq
    if (toImportSeq.forall(_.element.isInstanceOf[PsiClass])) classes
    else if (toImportSeq.forall(_.element.isInstanceOf[PsiPackage])) packages
    else mixed
  }

  def unapply(`type`: ElementToImport): Some[(PsiNamedElement, String)] =
    Some(`type`.element, `type`.name)
}

case class ClassToImport(element: PsiClass) extends ElementToImport {

  override protected type E = PsiClass

  def qualifiedName: String = element.qualifiedName

  override def isAnnotationType: Boolean = element.isAnnotationType
}

case class TypeAliasToImport(element: ScTypeAlias) extends ElementToImport {

  override protected type E = ScTypeAlias

  def qualifiedName: String = {
    val name = element.name

    val clazz = element.containingClass
    if (clazz == null || clazz.qualifiedName == "") name
    else clazz.qualifiedName + "." + name
  }
}

case class PrefixPackageToImport(element: ScPackage) extends ElementToImport {

  override protected type E = ScPackage

  def qualifiedName: String = element.getQualifiedName
}