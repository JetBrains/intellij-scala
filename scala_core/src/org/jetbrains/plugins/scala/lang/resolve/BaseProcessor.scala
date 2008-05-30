package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._

import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet

abstract class BaseProcessor(val kinds: Set[ResolveTargets]) extends PsiScopeProcessor {

  protected val candidates: HashSet[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def getCandidates = candidates

  def getHint[T](hintClass: Class[T]): T = {
    return null.asInstanceOf[T]
  }

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) = {}
}