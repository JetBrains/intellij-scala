package org.jetbrains.plugins.scala.lang.resolve.processors

/** 
* @author Ilya Sergey
*
*/

import com.intellij.psi.scope._
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor

import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._

trait ScalaPsiScopeProcessor extends PsiScopeProcessor {

  def getResult: PsiElement

/*
  def execute(element: PsiElement, substitutor: PsiSubstitutor): Boolean

  def getHint[T >: Null <: AnyRef](hintClass: Class[T]): T
*/                   

  //def handleEvent(event: Event, associated: Object) {}
}