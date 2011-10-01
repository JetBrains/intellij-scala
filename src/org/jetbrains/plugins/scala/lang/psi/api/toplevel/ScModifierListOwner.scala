package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import parser.ScalaElementTypes
import psi.stubs.ScModifiersStub
import stubs.{StubElement, NamedStub}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import util.PsiUtilBase
import com.intellij.util.{IncorrectOperationException, ArrayFactory}
import com.intellij.util.indexing.FileBasedIndex
import psi.impl.ScalaPsiElementFactory

/**
* @author ilyas
*/

trait ScModifierListOwner extends ScalaPsiElement with PsiModifierListOwner {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner")

  override def getModifierList: ScModifierList = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub: StubElement[_ <: PsiElement] = st.getStub
        if (stub != null) {
          val array = stub.getChildrenByType(ScalaElementTypes.MODIFIERS, JavaArrayFactoryUtil.ScModifierListFactory)
          if (array.length == 0) {
            val faultyContainer: VirtualFile = PsiUtilBase.getVirtualFile(this)
            LOG.error("Wrong Psi in Psi list: " + faultyContainer)
            if (faultyContainer != null && faultyContainer.isValid) {
              FileBasedIndex.getInstance.requestReindex(faultyContainer)
            }
            return null
          }
          else return array.apply(0)
        }
      }
      case _ =>
    }
    val res = findChildByClassScala(classOf[ScModifierList])
//    if (res == null) {
//      throw new IncorrectOperationException("null modifier list for: " + getText)
//    }
    res
  }

  def hasModifierProperty(name: String): Boolean = {
    this match {
      case st: ScalaStubBasedElementImpl[_] =>  {
        val stub: StubElement[_ <: PsiElement] = st.getStub
        if (stub != null) {
          val mod = stub.findChildStubByType(ScalaElementTypes.MODIFIERS)
          if (mod != null) {
            return mod.getPsi.hasModifierProperty(name: String)
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