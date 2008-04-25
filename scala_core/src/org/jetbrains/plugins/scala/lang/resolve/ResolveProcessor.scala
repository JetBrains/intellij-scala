package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._
import java.util.Set
import java.util.HashSet

class ResolveProcessor(val kinds : _root_.java.util.Set[ResolveTargets], val name : String) extends PsiScopeProcessor {

  val candidates : Set[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def getCandidates = candidates

  def execute(element : PsiElement, state : ResolveState) : Boolean = {
    candidates add new ScalaResolveResult(element)
    return true
  }

  def getHint[T](hintClass : Class[T]) : T = {
    return this.asInstanceOf[T]
  }

  def handleEvent(event : PsiScopeProcessor.Event, associated : Object) = {}
}