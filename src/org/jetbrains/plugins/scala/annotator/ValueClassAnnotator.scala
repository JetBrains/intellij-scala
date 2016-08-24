package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValueDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}

import scala.annotation.tailrec

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 10/12/15.
 */
trait ValueClassAnnotator {


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
  def annotateValueClass(valueClass: ScClass, holder: AnnotationHolder): Unit = {
    annotateValueClassConstructor(valueClass, holder)
    annotateValueClassTypeParameters(valueClass.typeParametersClause, holder)
    annotateInnerMembers(valueClass, holder: AnnotationHolder)
    annotateContainingClass(valueClass, holder, Option(valueClass.containingClass))
  }

  @tailrec
  private def annotateContainingClass(valueClass: ScClass, holder: AnnotationHolder,
                                      containingClass: Option[ScTemplateDefinition]): Unit = {
    containingClass match {
      case Some(obj: ScObject) => annotateContainingClass(valueClass, holder, Option(obj.containingClass)) //keep going
      case Some(_) => //value class is inside a trait or a class, need to highlight it
        holder.createErrorAnnotation(valueClass.nameId, ScalaBundle.message("value.classes.may.not.be.member.of.another.class"))
      case _ => //we are done, value class is either top level or inside a statically accessible object
    }
  }

  private def annotateInnerMembers(valueClass: ScClass, holder: AnnotationHolder): Unit = {
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

  private def annotateValueClassConstructor(valueClass: ScClass, holder: AnnotationHolder): Unit = {
    valueClass.constructor match {
      case Some(c) =>
        c.parameters match {
          case Seq(param) if !param.isPrivateThis && (param.isVal || param.isCaseClassVal) =>
          case Seq(param) => holder.createErrorAnnotation(param, ScalaBundle.message("value.class.can.have.only.val.parameter"))
          case _ =>
            holder.createErrorAnnotation(valueClass.nameId, ScalaBundle.message("value.class.can.have.only.one.parameter"))
        }
      case _ => //when is this possible?
    }

    valueClass.secondaryConstructors.foreach { constr =>
      holder.createErrorAnnotation(constr.nameId, ScalaBundle.message("illegal.secondary.constructors.value.class"))
    }
  }

  private def annotateValueClassTypeParameters(tp: Option[ScTypeParamClause], holder: AnnotationHolder): Unit = tp match {
    case Some(tpClause) => tpClause.typeParameters.filter(_.hasAnnotation("scala.specialized")).foreach {
      tpParam =>
        val message: String = ScalaBundle.message("type.parameter.value.class.may.not.be.specialized")
        holder.createErrorAnnotation(tpParam.nameId, message)
    }
    case _ =>
  }
}
