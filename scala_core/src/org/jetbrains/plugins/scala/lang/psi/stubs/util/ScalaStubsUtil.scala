package org.jetbrains.plugins.scala.lang.psi.stubs.util


import _root_.org.jetbrains.plugins.scala.lang.psi.stubs.index.ScDirectInheritorsIndex
import _root_.scala.collection.mutable.ArrayBuffer
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.search.GlobalSearchScope

import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.{PsiElement, PsiClass}

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaStubsUtil {
  def getClassInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTypeDefinition] = {
    val name: String = clazz.getName()
    if (name == null) return Seq.empty
    val inheritors = new ArrayBuffer[ScTypeDefinition]
    val extendsBlocks: java.util.Collection[ScExtendsBlock] = StubIndex.getInstance().get(ScDirectInheritorsIndex.KEY,
      name, clazz.getProject(), scope);
    for (exts <- extendsBlocks.toArray if exts.isInstanceOf[PsiElement]; ext = exts.asInstanceOf[PsiElement]) {
      val parent: PsiElement = ext.getParent()
      parent match {
        case x: ScTypeDefinition => inheritors += x
        case _ =>
      }
    }
    inheritors.toSeq
  }
}