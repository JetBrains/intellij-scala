package org.jetbrains.plugins.scala.lang.psi.stubs.util


import _root_.org.jetbrains.plugins.scala.lang.psi.stubs.index.{ScDirectInheritorsIndex, ScAnnotatedMemberIndex}
import _root_.scala.collection.mutable.ArrayBuffer
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.{PsiElement, PsiClass}
import java.util.ArrayList

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
      iterator.next.getParent match {
        case x: ScTypeDefinition => inheritors += x
        case _ =>
      }
    }
    inheritors.toSeq
  }

  def getAnnotatedMembers(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScMember] = {
    val name = clazz.getName
    if (name == null) return Seq.empty
    val members = new ArrayList[ScMember](StubIndex.getInstance.get(ScAnnotatedMemberIndex.KEY, name, clazz.getProject, scope))
    import _root_.scala.collection.jcl.Conversions.convertList
    members.toSeq
  }
}