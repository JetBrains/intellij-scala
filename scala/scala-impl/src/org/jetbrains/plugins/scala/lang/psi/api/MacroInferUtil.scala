package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern.extractProductParts
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}

/**
  * @author Alefas
  * @since 10/06/14.
  */
object MacroInferUtil {
  //todo fix decompiler and replace parameter by ScMacroDefinition
  def checkMacro(function: ScFunction,
                 expectedType: Option[ScType],
                 place: PsiElement): Option[ScType] = {
    def getClass(className: String) =
      place.elementScope.getCachedClass(s"shapeless.$className")

    val maybeGenericClass = getClass("Generic")
    val maybeGenericClassCompanion = maybeGenericClass.flatMap {
      ScalaPsiUtil.getCompanionModule
    }.collect {
      case scObject: ScObject => scObject
    }

    val classes: Seq[PsiClass] = (maybeGenericClass ++ maybeGenericClassCompanion ++ getClass("HNil") ++ getClass("::")).toSeq
    classes match {
      case Seq(genericClass: ScTypeDefinition, genericClassCompanion: ScObject, nilClass: PsiClass, consClass: PsiClass) =>
        def createGenericType(`type`: ScType) = extractProductParts(`type`, place).foldRight(ScDesignatorType(nilClass): ScType) {
          case (part, resultType) => ScParameterizedType(ScDesignatorType(consClass), Seq(part, resultType))
        } match {
          case _: ScDesignatorType => None
          case result => Some(result)
        }

        def calcProduct() = this.calcProduct(expectedType, genericClass, genericClassCompanion, createGenericType)

        Option(function).collect {
          case MacroDef(macroFunction) => macroFunction
        }.map {
          new Checker(_)
        }.map {
          _.withCheck("product", "Generic", calcProduct _)
        }.map {
          _.withCheck("apply", "LowPriorityGeneric", calcProduct _)
        }.flatMap {
          _.check()
        }
      case _ => None
    }
  }

  private class Checker(function: ScFunction,
                        checkers: List[() => Option[ScType]] = Nil) {

    def withCheck(functionName: String,
                  className: String,
                  checker: () => Option[ScType]): Checker =
      new Checker(function,
        wrapper(functionName, className, checker) :: checkers)

    def check(): Option[ScType] = checkers.flatMap {
      _.apply()
    }.headOption

    def wrapper(functionName: String,
                className: String,
                checker: () => Option[ScType]): () => Option[ScType] =
      () => Option(function).filter {
        _.name == functionName
      }.flatMap { element =>
        Option(element.containingClass)
      }.filter {
        _.qualifiedName == s"shapeless.$className"
      }.flatMap { _ =>
        checker()
      }
  }

  private def calcProduct(maybeExpectedType: Option[ScType],
                          clazz: ScTypeDefinition,
                          classCompanion: ScObject,
                          createGenericType: ScType => Option[ScType]): Option[ScType] = {
    import clazz.projectContext

    val maybeProjectionType = classCompanion.members.collect {
      case alias: ScTypeAlias => alias
    }.find {
      _.name == "Aux"
    }.map {
      ScProjectionType(ScDesignatorType(classCompanion), _, superReference = false)
    }

    clazz.typeParameters.headOption
      .map(UndefinedType(_))
      .flatMap { undefinedType =>
        maybeExpectedType.flatMap {
          _.conforms(ScParameterizedType(ScDesignatorType(clazz), Seq(undefinedType)), ScUndefinedSubstitutor()) match {
            case (true, substitutor) =>
              substitutor.getSubstitutor.map {
                _.subst(undefinedType)
              }.flatMap { productLikeType =>
                createGenericType(productLikeType).flatMap { resultType =>
                  maybeProjectionType.map {
                    ScParameterizedType(_, Seq(productLikeType, resultType))
                  }
                }
              }
            case _ => None
          }
        }
      }
  }

}
