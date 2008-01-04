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
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs._

class ScalaLocalVariableResolveProcessor(val myName: String, val offset: Int, val myElement: PsiElement) extends ScalaPsiScopeProcessor {

  protected var myResult: PsiElement = null

  val canBeObject: Boolean = false

  def getResult: PsiElement = myResult

  def setResult(result: PsiElement) {
    myResult = result
  }

  def getName = myName

  // Process variable
  def execute(element: PsiElement, substitutor: PsiSubstitutor): Boolean = {
    if (element.isInstanceOf[ScReferenceIdContainer]) {

      val valDef = element.asInstanceOf[ScReferenceIdContainer]
      def tryToFinish: Boolean = {
        for (val value <- valDef.getNames) {
          if (value.getText.equals(myName) &&
          value.getTextOffset <= offset) {
            myResult = value
            return false
          }
        }
        true
      }

      valDef match {
        case fun: ScFunction => {
          if (myElement.getParent.isInstanceOf[ScMethodCallImpl] &&
          fun.getFunctionName.equals(myName) &&
          fun.getAbstractType != null &&
          fun.getAbstractType.canBeAppliedTo(myElement.getParent.asInstanceOf[ScMethodCallImpl].getAllArgumentsTypes)) {
            tryToFinish
          } else if  (fun.getFunctionName.equals(myName) && fun.getAbstractType != null &&
          (fun.getAbstractType.funParams == null || fun.getAbstractType.funParams.length == 0)){
            tryToFinish
          } else {
            true
          }
        }
        case _ => tryToFinish
      }
    } else {
      true
    }
  }

  def getHint[T](hintClass: java.lang.Class[T]): T = {
    null.asInstanceOf[T]
  }


  def handleEvent(event: Event, associated: Object): Unit = {}

}