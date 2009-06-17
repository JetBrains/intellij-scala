package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi._
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.psi.api.base._
import parser.ScalaElementTypes
import psi.stubs.ScModifiersStub
import stubs.{StubElement, NamedStub}
/**
* @author ilyas
*/

trait ScModifierListOwner extends ScalaPsiElement with PsiModifierListOwner {

  override def getModifierList: ScModifierList = {
    this match {
      case st: StubBasedPsiElement[_] => {
        val stub: StubElement[_] = st.getStub
        if (stub != null) {
          val array = stub.getChildrenByType(ScalaElementTypes.MODIFIERS, new ArrayFactory[ScModifierList] {
            def create(count: Int): Array[ScModifierList] = new Array[ScModifierList](count)
          })
          if (array.length == 0) return null
          else return array.apply(0)
        }
      }
      case _ =>
    }
    findChildByClass(classOf[ScModifierList])
  }

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