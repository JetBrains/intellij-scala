package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

object ScOverriddenVarAnnotator extends ElementAnnotator[ScTypedDefinition] {
  override def annotate(elem: ScTypedDefinition, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val isAllowed = elem.nameContext match {
      case o: ScModifierListOwner if !o.getModifierList.modifiers.contains(ScalaModifier.Override) =>
        true
      case p: ScClassParameter if p.isVar =>
        val supers = findSupers(p, elem.name)
        if (supers.nonEmpty) isOverrideOfAbstract(supers) else true
      case v: ScVariable =>
        val supers = findSupers(v, elem.name)
        if (supers.nonEmpty) isOverrideOfAbstract(supers) else true
      case f: ScFunction if isSetter(f) =>
        val setterName = f.name.dropRight(2)
        val supers = findSupers(f, setterName)
        if (supers.exists(isVar)) hasGetter(f, setterName) else true
      case f: ScFunction if isGetter(f) =>
        val supers = findSupers(f, elem.name)
        if (supers.exists(isVar)) hasSetter(f) else true
      case _ =>
        true
    }

    if (!isAllowed)
      holder.createErrorAnnotation(elem.getIdentifyingElement, ScalaBundle.message("var.cannot.be.overridden"))
  }

  private def findSupers(m: ScMember, name: String): Seq[PsiElement] =
    m.containingClass.supers.collect {
      case t: ScTypeDefinition =>
        t.allTermsByName(name).collect {
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

  private def hasGetter(elem: ScFunction, name: String): Boolean =
    elem.containingClass.allFunctionsByName(name).exists {
      case f: ScFunction if isGetter(f) =>
        val elemType = elem.parameters.head.typeElement.map(_.getText).getOrElse("")
        f.returnTypeElement.exists(_.textMatches(elemType))
      case _ =>
        false
    }

  private def hasSetter(elem: ScFunction): Boolean =
    elem.containingClass.allFunctionsByName(elem.name + "_=").exists {
      case f: ScFunction if isSetter(f) =>
        val elemType = elem.returnTypeElement.map(_.getText).getOrElse("")
        f.parameters.head.typeElement.exists(_.textMatches(elemType))
      case _ =>
        false
    }

  private def isSetter(f: ScFunction): Boolean =
    f.name.endsWith("_=") && f.hasUnitResultType && f.parameters.size == 1

  private def isGetter(f: ScFunction): Boolean =
    !f.name.endsWith("_=") && !f.hasUnitResultType && f.parameters.isEmpty
}
