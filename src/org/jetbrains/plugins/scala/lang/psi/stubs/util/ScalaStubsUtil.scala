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

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaStubsUtil {
  def getClassInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTypeDefinition] = {
    val name: String = clazz.getName()
    if (name == null) return Seq.empty
    val inheritors = new ArrayBuffer[ScTypeDefinition]
    val iterator: java.util.Iterator[ScExtendsBlock] =
      StubIndex.getInstance().get(ScDirectInheritorsIndex.KEY, name, clazz.getProject(), scope).iterator
    while (iterator.hasNext) {
      val extendsBlock = iterator.next
      val stub = extendsBlock.asInstanceOf[ScExtendsBlockImpl].getStub
      if (stub != null) {
        if (stub.getParentStub.getStubType.isInstanceOf[ScTypeDefinitionElementType[_]]) {
          inheritors += stub.getParentStub.getPsi.asInstanceOf[ScTypeDefinition]
        }
      } else {
        extendsBlock.getParent match {
          case tp: ScTypeDefinition => inheritors += tp
          case _ =>
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
}