package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScValueOrVariable, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}

object ScOverriddenVarAnnotator extends ElementAnnotator[ScTypedDefinition] {
  override def annotate(elem: ScTypedDefinition, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val errorMessage = elem.nameContext match {
      case o: ScModifierListOwner if !o.getModifierList.isOverride =>
        None
      case v: ScValueOrVariable if v.isAbstract =>
        None
      case p: ScClassParameter if p.isAbstractMember =>
        None
      case f: ScFunction if f.isAbstractMember =>
        None
      case p: ScClassParameter if p.isVar =>
        val supers = findSupers(elem.name, p.containingClass)
        if (supers.nonEmpty && !isOverrideOfAbstract(supers))
          Some(ScalaBundle.message("var.cannot.be.overridden"))
        else
          None
      case v: ScVariable =>
        val supers = findSupers(elem.name, v.containingClass)
        if (supers.nonEmpty && !isOverrideOfAbstract(supers))
          Some(ScalaBundle.message("var.cannot.be.overridden"))
        else
          None
      case f: ScFunction if f.name.endsWith("_=") && isSetter(f) =>
        val elemName = f.name.dropRight(2)
        val containingClass = f.containingClass
        val supers = findSupers(elemName, containingClass)
        if (supers.exists(isVar) && !hasGetter(elemName, containingClass))
          Some(ScalaBundle.message("missing.getter.implementation", elemName))
        else
          None
      case f: ScFunction if isGetter(f) =>
        checkForNonAbstractVarSuper(elem.name, f.containingClass)
      case v: ScValue =>
        checkForNonAbstractVarSuper(elem.name, v.containingClass)
      case p: ScClassParameter =>
        val supers = findSupers(elem.name, p.containingClass)
        if (supers.exists(isVar))
          Some(ScalaBundle.message("var.cannot.be.overridden"))
        else
          None
      case _ =>
        None
    }

    errorMessage.foreach(holder.createErrorAnnotation(elem.getIdentifyingElement, _))
  }

  private def checkForNonAbstractVarSuper(elemName: String, containingClass: ScTemplateDefinition): Option[String] =
    findSupers(elemName, containingClass).collectFirst {
      case p: ScClassParameter if p.isVar =>
        ScalaBundle.message("var.cannot.be.overridden")
      case v: ScVariable if v.isAbstract && !hasSetter(elemName, containingClass) =>
        ScalaBundle.message("missing.setter.implementation", elemName)
      case v: ScVariable if !v.isAbstract =>
        ScalaBundle.message("var.cannot.be.overridden")
    }

  private def findSupers(elemName: String, containingClass: ScTemplateDefinition): Seq[PsiElement] =
    containingClass.supers.collect {
      case t: ScTypeDefinition =>
        t.allTermsByName(elemName).collect {
          case term: ScTypedDefinition => term.nameContext
        }
    }.flatten

  private def isVar(elem: PsiElement): Boolean =
    elem match {
      case p: ScClassParameter => p.isVar
      case _: ScVariable       => true
      case _                   => false
    }

  private def isOverrideOfAbstract(supers: Seq[PsiElement]): Boolean =
    supers.exists {
      case v: ScVariable       => v.isAbstract
      case s: ScClassParameter => s.isAbstractMember
      case _                   => false
    }

  private def hasGetter(elemName: String, containingClass: ScTemplateDefinition): Boolean =
    if (containingClass.allVals.exists(v => !v.isAbstract && v.name == elemName))
      true
    else
      containingClass.allFunctionsByName(elemName).exists {
        case f: ScFunction if isGetter(f) => true
        case _ => false
      }

  private def hasSetter(elemName: String, containingClass: ScTemplateDefinition): Boolean =
    containingClass.allFunctionsByName(elemName + "_=").exists {
      case f: ScFunction if isSetter(f) => true
      case _ => false
    }

  private def isSetter(f: ScFunction): Boolean =
    f.hasUnitResultType && f.parameters.size == 1

  private def isGetter(f: ScFunction): Boolean =
    !f.hasUnitResultType && f.parameters.isEmpty
}
