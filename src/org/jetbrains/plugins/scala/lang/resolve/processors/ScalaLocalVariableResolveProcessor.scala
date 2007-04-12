package org.jetbrains.plugins.scala.lang.resolve.processors

/**
* @author Ilya Sergey
*
*/

import com.intellij.psi.scope._
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.scope.PsiScopeProcessor.Event

import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._

class ScalaLocalVariableResolveProcessor(val myName: String, val offset: Int) extends ScalaPsiScopeProcessor {

  protected var myResult: PsiElement = null

  val canBeObject: Boolean = false

  def getResult: PsiElement = myResult

  def setResult(result: PsiElement) {
    myResult = result
  }

  def getName = myName

  // Process variable
  def execute(element: PsiElement, substitutor: PsiSubstitutor): Boolean = {
    if (element.isInstanceOf[Referenced]) {
      val valDef = element.asInstanceOf[Referenced]
      for (val value <- valDef.getNames) {
        if (value.getText.equals(myName) &&
        value.getTextOffset <= offset) {
          myResult = value
          // TODO type output!
          //Console.println(varDef.getExplicitType)
          return false
        }
      }
    }
    true
  }

  def getHint[T >: Null <: java.lang.Object](hintClass: java.lang.Class[T]): T = {
    null
  }


  def handleEvent(event: Event, associated: Object): Unit = {}

}