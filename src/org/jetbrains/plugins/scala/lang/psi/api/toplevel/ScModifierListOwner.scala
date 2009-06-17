package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import psi.stubs.ScModifiersStub
import stubs.{StubElement, NamedStub}
/**
* @author ilyas
*/

trait ScModifierListOwner extends ScalaPsiElement with PsiModifierListOwner {

  override def getModifierList: ScModifierList = findChildByClass(classOf[ScModifierList])

  def hasModifierProperty(name: String): Boolean = {
    this match {
      case st: StubBasedPsiElement[_] =>  {
        val stub: StubElement[_] = st.getStub
        if (stub != null) {
          import collection.jcl.Conversions._
          for (child <- stub.getChildrenStubs if child.isInstanceOf[ScModifiersStub]) {
            val mStub: ScModifiersStub = child.asInstanceOf[ScModifiersStub]
            return mStub.getModifiers.exists(name == _)
          }
          return false
        }
      }
      case _ =>
    }
    if (getModifierList != null)
      getModifierList.hasModifierProperty(name: String)
    else false
  }

  def setModifierProperty(name: String, value: Boolean) {
    getModifierList.setModifierProperty(name, value)
  }
}