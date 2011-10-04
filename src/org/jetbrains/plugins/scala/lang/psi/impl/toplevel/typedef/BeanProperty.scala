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
    for (method <- t.getBeanMethods) {
      if (!callback(method)) return false
    }
    true
  }
}
