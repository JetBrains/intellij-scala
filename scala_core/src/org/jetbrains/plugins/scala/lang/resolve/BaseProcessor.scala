package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._
import java.util.Set
import java.util.HashSet

abstract class BaseProcessor(val kinds: Set[ResolveTargets]) extends PsiScopeProcessor {

  protected val candidates: Set[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def getCandidates = candidates

  def getHint[T](hintClass: Class[T]): T = {
    return null.asInstanceOf[T]
  }

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) = {}
}