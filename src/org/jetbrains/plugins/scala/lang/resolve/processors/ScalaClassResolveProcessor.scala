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

class ScalaClassResolveProcessor(val myName: String, val offset: Int) extends ScalaPsiScopeProcessor {
                            
  protected var myResult: PsiElement = null

  val canBeObject : Boolean = false

  def getResult: PsiElement = myResult   
                                        
  def setResult(result: PsiElement) {
    import org.jetbrains.plugins.scala.lang.psi.javaView.ScJavaClass
    myResult =  if (result.isInstanceOf[ScJavaClass]) result.asInstanceOf[ScJavaClass].scClass else result
  }                            

  def getName = myName

  import com.intellij.psi._

  def execute(element: PsiElement, substitutor: ResolveState): Boolean = {
    if (element.isInstanceOf[ScTmplDef]) {
      if (element.asInstanceOf[ScTmplDef].getName.equals(myName) &&
      ! element.isInstanceOf[ScObjectDefinition]) {
        myResult = element
        return false
      }
    }
    true
  }


  def getHint[T](hintClass: java.lang.Class[T]): T = {
    null.asInstanceOf[T]
  }


  def handleEvent(event: Event, associated: Object): Unit = {}

}