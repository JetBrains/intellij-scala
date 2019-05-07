package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isImplicit
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author adkozlov
  */
final class ImplicitConversionProcessor(override val getPlace: ScExpression,
                                        override protected val withoutPrecedence: Boolean)
  extends ImplicitProcessor(getPlace, withoutPrecedence) {

  private val functionType = getPlace.elementScope.function1Type(level = 0)

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {

    if (ImplicitConversionProcessor.shouldProcess(namedElement, getPlace)) {
      namedElement match {
        case f: ScFunction if !f.isParameterless => addIfImplicitConversion(f)
        case t: Typeable                         => addIfHasFunctionType(t)
        case _                                   =>
      }
    }

    true
  }

  private def addIfHasFunctionType(namedElement: PsiNamedElement with Typeable)
                                  (implicit state: ResolveState): Unit = {
    val subst: ScSubstitutor = getSubstWithThisType(state)
    val elemType = subst(namedElement.`type`().getOrAny)

    if (functionType.exists(!elemType.conforms(_))) true
    else addResult(new ScalaResolveResult(namedElement, subst, getImports(state)))
  }

  private def addIfImplicitConversion(function: ScFunction)
                                     (implicit state: ResolveState): Unit = {
    if (!function.isImplicitConversion) true
    else addResult(new ScalaResolveResult(function, getSubstWithThisType(state), getImports(state)))
  }
}


object ImplicitConversionProcessor {
  private def shouldProcess(namedElement: PsiNamedElement, place: PsiElement): Boolean = {
    isImplicit(namedElement) &&
      !isConformsMethod(namedElement) &&
      ImplicitProcessor.isAccessible(namedElement, place)
  }

  private def isConformsMethod(named: PsiNamedElement) = named match {
    case f: ScFunction =>
      (f.name == "conforms" || f.name == "$conforms") &&
        Option(f.containingClass).flatMap(cls => Option(cls.qualifiedName)).contains("scala.Predef")
    case _ => false
  }
}

