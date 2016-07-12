package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScMacroDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, TypeSystem, UndefinedType}

/**
  * @author Alefas
  * @since 10/06/14.
  */
object MacroInferUtil {
  //todo fix decompiler and replace parameter by ScMacroDefinition
  def checkMacro(function: ScFunction,
                 expectedType: Option[ScType],
                 place: PsiElement)
                (implicit typeSystem: TypeSystem): Option[ScType] = {
    def calcProduct() = this.calcProduct(expectedType, place)

    Option(function) collect {
      case IsMacro(macroFunction) => macroFunction
    } map {
      new Checker(_)
    } map {
      _.withCheck("product", "Generic", calcProduct)
    } map {
      _.withCheck("apply", "LowPriorityGeneric", calcProduct)
    } flatMap {
      _.check()
    }
  }

  class Checker(function: ScFunction,
                checkers: List[() => Option[ScType]] = Nil) {

    def withCheck(functionName: String,
                  className: String,
                  checker: () => Option[ScType]): Checker =
      new Checker(function,
        wrapper(functionName, className, checker) :: checkers)

    def check(): Option[ScType] = checkers flatMap {
      _.apply()
    } headOption

    def wrapper(functionName: String,
                className: String,
                checker: () => Option[ScType]): () => Option[ScType] =
      () => Option(function) filter {
        _.name == functionName
      } flatMap { element =>
        Option(element.containingClass)
      } filter {
        _.qualifiedName == s"shapeless.$className"
      } flatMap { _ =>
        checker()
      }
  }

  def calcProduct(expectedType: Option[ScType], place: PsiElement): Option[ScType] = expectedType match {
    case Some(tp) =>
      val manager = ScalaPsiManager.instance(place.getProject)
      val clazz = manager.getCachedClass("shapeless.Generic", place.getResolveScope, ClassCategory.TYPE)
      clazz match {
        case c: ScTypeDefinition =>
          val tpt = c.typeParameters
          if (tpt.isEmpty) return None
          val undef = UndefinedType(TypeParameterType(tpt.head))
          val genericType = ScParameterizedType(ScDesignatorType(c), Seq(undef))
          val (res, undefSubst) = tp.conforms(genericType, new ScUndefinedSubstitutor())
          if (!res) return None
          undefSubst.getSubstitutor match {
            case Some(subst) =>
              val productLikeType = subst.subst(undef)
              val parts = ScPattern.extractProductParts(productLikeType, place)
              if (parts.isEmpty) return None
              val coloncolon = manager.getCachedClass("shapeless.::", place.getResolveScope, ClassCategory.TYPE)
              if (coloncolon == null) return None
              val hnil = manager.getCachedClass("shapeless.HNil", place.getResolveScope, ClassCategory.TYPE)
              if (hnil == null) return None
              val repr = parts.foldRight(ScDesignatorType(hnil): ScType) {
                case (part, resultType) => ScParameterizedType(ScDesignatorType(coloncolon), Seq(part, resultType))
              }
              ScalaPsiUtil.getCompanionModule(c) match {
                case Some(obj: ScObject) =>
                  val elem = obj.members.find {
                    case a: ScTypeAlias if a.name == "Aux" => true
                    case _ => false
                  }
                  if (elem.isEmpty) return None
                  Some(ScParameterizedType(ScProjectionType(ScDesignatorType(obj), elem.get.asInstanceOf[PsiNamedElement],
                    superReference = false), Seq(productLikeType, repr)))
                case _ => None
              }
            case _ => None
          }
        case _ => None
      }
    case None => None
  }

  private object IsMacro {
    def unapply(element: PsiNamedElement): Option[ScFunction] = Option(element) collect {
      case function: ScMacroDefinition => function
      case function: ScFunction if function.hasAnnotation("scala.reflect.macros.internal.macroImpl") =>
        function
    }
  }
}
