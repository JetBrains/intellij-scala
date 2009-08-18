package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package util


import _root_.org.jetbrains.plugins.scala.lang.psi.stubs.index.{ScDirectInheritorsIndex, ScAnnotatedMemberIndex}
import _root_.scala.collection.mutable.ArrayBuffer
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.{PsiElement, PsiClass}
import elements.ScTypeDefinitionElementType
import java.util.ArrayList
import psi.impl.toplevel.templates.ScExtendsBlockImpl
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.psi.util.{PsiUtilBase, PsiUtil}
import com.intellij.openapi.diagnostic.Logger

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaStubsUtil {
  def getClassInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTypeDefinition] = {
    val name: String = clazz.getName()
    if (name == null) return Seq.empty
    val inheritors = new ArrayBuffer[ScTypeDefinition]
    val iterator: java.util.Iterator[PsiElement] =
      StubIndex.getInstance().get(ScDirectInheritorsIndex.KEY, name, clazz.getProject(), scope).iterator.
              asInstanceOf[java.util.Iterator[PsiElement]]
    while (iterator.hasNext) {
      val extendsBlock: PsiElement = iterator.next
      if (checkPsi(extendsBlock, classOf[ScExtendsBlock])) {
        val stub = extendsBlock.asInstanceOf[ScExtendsBlockImpl].getStub
        if (stub != null) {
          if (stub.getParentStub.getStubType.isInstanceOf[ScTypeDefinitionElementType[_ <: ScTypeDefinition]]) {
            inheritors += stub.getParentStub.getPsi.asInstanceOf[ScTypeDefinition]
          }
        }
        else {
          extendsBlock.getParent match {
            case tp: ScTypeDefinition => inheritors += tp
            case _ =>
          }
        }
      }
    }
    inheritors.toSeq
  }

  def getAnnotatedMembers(clazz: PsiClass, scope: GlobalSearchScope): List[ScMember] = {
    val name = clazz.getName
    if (name == null) return Nil
    List.fromArray(StubIndex.getInstance.get(ScAnnotatedMemberIndex.KEY, name, clazz.getProject, scope).toArray(Array[ScMember]()))
  }

  def checkPsi[T <: PsiElement](element: PsiElement, clazz: Class[T]): Boolean = {
    element match {
      case x: T => return true
      case _ => {
        val faultyContainer = PsiUtilBase.getVirtualFile(element)
        LOG.error("Wrong Psi in Psi list: " + faultyContainer)
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        return false
      }
    }
  }

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil")
}