package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValueDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType

import scala.annotation.tailrec

object ScClassAnnotator extends ElementAnnotator[ScClass] {

  override def annotate(element: ScClass, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware && ValueClassType.extendsAnyVal(element)) {
      annotateValueClass(element)
    }
  }

  /**
    * A value class …
    *
    *  … must have only a primary constructor with exactly one public, val parameter whose type is not a value class. (From Scala 2.11.0, the parameter may be non-public.)
    *  … may not have specialized type parameters.
    *  … may not have nested or local classes, traits, or objects
    *  … may not define a equals or hashCode method.
    *  … must be a top-level class or a member of a statically accessible object
    *  … can only have defs as members. In particular, it cannot have lazy vals, vars, or vals as members.
    *  … cannot be extended by another class.
    *  @see SCL-9263
    */
  def annotateValueClass(valueClass: ScClass)
                        (implicit holder: ScalaAnnotationHolder): Unit = {
    annotateValueClassConstructor(valueClass)
    annotateValueClassTypeParameters(valueClass.typeParametersClause)
    annotateInnerMembers(valueClass)
    annotateContainingClass(valueClass, Option(valueClass.containingClass))
  }

  @tailrec
  private def annotateContainingClass(valueClass: ScClass, containingClass: Option[ScTemplateDefinition])
                                     (implicit holder: ScalaAnnotationHolder): Unit = {
    containingClass match {
      case Some(obj: ScObject) => annotateContainingClass(valueClass, Option(obj.containingClass)) //keep going
      case Some(_) => //value class is inside a trait or a class, need to highlight it
        holder.createErrorAnnotation(valueClass.nameId, ScalaBundle.message("value.classes.may.not.be.member.of.another.class"))
      case _ => //we are done, value class is either top level or inside a statically accessible object
    }
  }

  private def annotateInnerMembers(valueClass: ScClass)
                                  (implicit holder: ScalaAnnotationHolder): Unit = {
    valueClass.allInnerTypeDefinitions.foreach { td =>
      holder.createErrorAnnotation(td.nameId, ScalaBundle.message("value.classes.cannot.have.nested.objects"))
    }
    valueClass.functions.foreach {
      case fun if fun.name == "equals" || fun.name == "hashCode" =>
        holder.createErrorAnnotation(fun.nameId, ScalaBundle.message("value.classes.cannot.redefine.equals.hashcode"))
      case _ =>
    }
    valueClass.members.foreach {
      case pat: ScPatternDefinition => pat.declaredElements.foreach { named =>
        holder.createErrorAnnotation(named.nameId, ScalaBundle.message("value.classes.can.have.only.defs"))
      }
      case valDef: ScValueDeclaration => valDef.declaredElements.foreach { named =>
        holder.createErrorAnnotation(named.nameId, ScalaBundle.message("value.classes.can.have.only.defs"))
      }
      case varDef: ScVariableDefinition => varDef.declaredElements.foreach { named =>
        holder.createErrorAnnotation(named.nameId, ScalaBundle.message("value.classes.can.have.only.defs"))
      }
      case _ =>
    }
  }

  private def annotateValueClassConstructor(valueClass: ScClass)
                                           (implicit holder: ScalaAnnotationHolder): Unit = {
    valueClass.constructor match {
      case Some(c) =>
        c.parameters match {
          case collection.Seq(param) if !param.isPrivateThis && (param.isVal || param.isCaseClassVal) =>
          case collection.Seq(param) => holder.createErrorAnnotation(param, ScalaBundle.message("value.class.can.have.only.val.parameter"))
          case _ =>
            holder.createErrorAnnotation(valueClass.nameId, ScalaBundle.message("value.class.can.have.only.one.parameter"))
        }
      case _ => //when is this possible?
    }

    valueClass.secondaryConstructors.foreach { constr =>
      holder.createErrorAnnotation(constr.nameId, ScalaBundle.message("illegal.secondary.constructors.value.class"))
    }
  }

  private def annotateValueClassTypeParameters(tp: Option[ScTypeParamClause])
                                              (implicit holder: ScalaAnnotationHolder): Unit = tp match {
    case Some(tpClause) => tpClause.typeParameters.filter(_.hasAnnotation("scala.specialized")).foreach {
      tpParam =>
        val message: String = ScalaBundle.message("type.parameter.value.class.may.not.be.specialized")
        holder.createErrorAnnotation(tpParam.nameId, message)
    }
    case _ =>
  }
}