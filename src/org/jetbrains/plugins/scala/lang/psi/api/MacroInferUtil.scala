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
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem

/**
 * @author Alefas
 * @since 10/06/14.
 */
object MacroInferUtil {
  //todo fix decompiler and replace parameter by ScMacroDefinition
  def checkMacro(f: ScFunction, expectedType: Option[ScType], place: PsiElement)
                (implicit typeSystem: TypeSystem): Option[ScType] = {
    if (!f.isInstanceOf[ScMacroDefinition] && !f.hasAnnotation("scala.reflect.macros.internal.macroImpl").isDefined) {
      return None
    }

    class Checker(l: List[() => Option[ScType]] = List.empty) {
      def withCheck(checker: () => Option[ScType]): Checker = new Checker(checker :: l)
      def withCheck(functionName: String, classFqn: String, typeEval: () => Option[ScType]): Checker = {
        withCheck(() => {
          if (f.name != functionName) None
          else {
            val clazz = f.containingClass
            if (clazz == null) None
            else {
              if (clazz.qualifiedName != classFqn) None
              else typeEval()
            }
          }
        })
      }

      def check(): Option[ScType] = {
        for {
          f <- l
          res <- f()
        } return Some(res)
        None
      }
    }

    def calcProduct(): Option[ScType] = {
      expectedType match {
        case Some(tp) =>
          val manager = ScalaPsiManager.instance(place.getProject)
          val clazz = manager.getCachedClass("shapeless.Generic", place.getResolveScope, ClassCategory.TYPE)
          clazz match {
            case c: ScTypeDefinition =>
              val tpt = c.typeParameters
              if (tpt.length == 0) return None
              val undef = new ScUndefinedType(new ScTypeParameterType(tpt(0), ScSubstitutor.empty))
              val genericType = ScParameterizedType(ScDesignatorType(c), Seq(undef))
              val (res, undefSubst) = Conformance.conformsInner(genericType, tp, Set.empty, new ScUndefinedSubstitutor())
              if (!res) return None
              undefSubst.getSubstitutor match {
                case Some(subst) =>
                  val productLikeType = subst.subst(undef)
                  val parts = ScPattern.extractProductParts(productLikeType, place)
                  if (parts.length == 0) return None
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
                      if (!elem.isDefined) return None
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
    }

    new Checker().
      withCheck("product", "shapeless.Generic", calcProduct).
      withCheck("apply", "shapeless.LowPriorityGeneric", calcProduct).
      check()
  }

  def isMacro(n: PsiNamedElement): Option[ScFunction] = {
    n match {
      case f: ScMacroDefinition => Some(f)
      //todo: fix decompiler to avoid this check:
      case f: ScFunction if f.hasAnnotation("scala.reflect.macros.internal.macroImpl").isDefined => Some(f)
      case _ => None
    }
  }
}
