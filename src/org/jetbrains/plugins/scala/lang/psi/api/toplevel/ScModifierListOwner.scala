package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
* @author ilyas
*/

trait ScModifierListOwner extends ScalaPsiElement with PsiModifierListOwner {
  override def getModifierList: ScModifierList = {
    this match {
      case st: ScalaStubBasedElementImpl[_] =>
        val stub: StubElement[_ <: PsiElement] = st.getStub
        if (stub != null) {
          val array = stub.getChildrenByType(ScalaElementTypes.MODIFIERS, JavaArrayFactoryUtil.ScModifierListFactory)
          if (array.isEmpty) {
            val faultyContainer: VirtualFile = PsiUtilCore.getVirtualFile(this)
            if (faultyContainer != null && faultyContainer.isValid) {
              FileBasedIndex.getInstance.requestReindex(faultyContainer)
            }
            throw new Throwable("Stub hasn't ScModifierList child: " + faultyContainer)
          }
          else return array.apply(0)
        }
      case _ =>
    }
    findChildByClassScala(classOf[ScModifierList])
  }

  def hasModifierProperty(name: String): Boolean = {
    hasModifierPropertyInner(name)
  }

  def hasModifierPropertyScala(name: String): Boolean = {
    if (name == PsiModifier.PUBLIC) {
      return !hasModifierPropertyScala("private") && !hasModifierPropertyScala("protected")
    }
    hasModifierPropertyInner(name)
  }

  def hasAbstractModifier: Boolean = hasModifierPropertyInner("abstract")

  def hasFinalModifier: Boolean = hasModifierPropertyInner("final")

  private def hasModifierPropertyInner(name: String): Boolean = {
    this match {
      case st: ScalaStubBasedElementImpl[_] =>
        val stub: StubElement[_ <: PsiElement] = st.getStub
        if (stub != null) {
          val mod = stub.findChildStubByType(ScalaElementTypes.MODIFIERS)
          if (mod != null) {
            return mod.getPsi.hasModifierProperty(name: String)
          }
          return false
        }
      case _ =>
    }
    if (getModifierList != null) getModifierList.hasModifierProperty(name: String)
    else false
  }

  def setModifierProperty(name: String, value: Boolean) {
    getModifierList.setModifierProperty(name, value)
  }
}