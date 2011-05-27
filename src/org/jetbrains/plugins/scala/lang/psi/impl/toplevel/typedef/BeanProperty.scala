package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.psi.scope.PsiScopeProcessor
import lang.psi.api.statements.params.ScClassParameter
import lang.psi.api.statements.{ScVariable, ScValue, ScAnnotationsHolder}
import lang.psi.api.toplevel.ScTypedDefinition
import lang.psi.fake.FakePsiMethod
import lang.psi.types.nonvalue.Parameter
import lang.psi.types.result.TypingContext
import com.intellij.openapi.util.text.StringUtil
import lang.psi.types.{Any, ScType, Unit}
import com.intellij.psi.{PsiMethod, ResolveState, PsiElement}


object BeanProperty {
  def processBeanPropertyDeclarations(annotated: ScAnnotationsHolder, context: PsiElement,
                                      processor: PsiScopeProcessor, t: ScTypedDefinition, state: ResolveState): Boolean = {
    processBeanPropertyDeclarationsInternal(annotated, context, t) { element =>
      processor.execute(element, state)
    }
  }

  def processBeanPropertyDeclarationsInternal(annotated: ScAnnotationsHolder, context: PsiElement, t: ScTypedDefinition)
                                             (callback: (PsiMethod => Boolean)): Boolean = {
    implicit def arr2arr(a: Array[ScType]): Array[Parameter] = a.map(Parameter("", _, false, false, false))
    def has(annotationName: String): Boolean = annotated.hasAnnotation(annotationName).isDefined
    val isBeanProperty = has("scala.reflect.BeanProperty")
    val isBooleanBeanProperty = has("scala.reflect.BooleanBeanProperty")

    if (!(isBeanProperty || isBooleanBeanProperty)) return true

    val getterName: String = {
      val prefix = if (isBeanProperty) "get" else "is"
      prefix + StringUtil.capitalize(t.getName)
    }
    val setterName = "set" + StringUtil.capitalize(t.getName)
    def tType = t.getType(TypingContext.empty).getOrElse(Any)

    context match {
      case value: ScValue => {
        if (!callback(new FakePsiMethod(t, getterName, Array.empty, tType, value.hasModifierProperty _))) return false
      }
      case variable: ScVariable => {
        if (!callback(new FakePsiMethod(t, getterName, Array.empty, tType, variable.hasModifierProperty _))) return false
        if (!callback(new FakePsiMethod(t, setterName, Array[ScType](tType), Unit, variable.hasModifierProperty _))) return false
      }
      case param: ScClassParameter if param.isVal => {
        if (!callback(new FakePsiMethod(t, getterName, Array.empty, tType, param.hasModifierProperty _))) return false
      }
      case param: ScClassParameter if param.isVar => {
        if (!callback(new FakePsiMethod(t, getterName, Array.empty, tType, param.hasModifierProperty _))) return false
        if (!callback(new FakePsiMethod(t, setterName, Array[ScType](tType), Unit, param.hasModifierProperty _))) return false
      }
      case _ =>
    }
    true
  }
}
