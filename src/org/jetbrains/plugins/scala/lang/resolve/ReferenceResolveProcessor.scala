package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.scope._
import com.intellij.psi._

abstract class ReferenceResolveProcessor extends PsiScopeProcessor {

  def execute(element : PsiElement, state : ResolveState) : Boolean = {

    return true
  }

  def getHint[T](hintClass : Class[T]) : T

  def handleEvent(event : PsiScopeProcessor.Event, associated : Object) = {}
}