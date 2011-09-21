package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package util


import _root_.org.jetbrains.plugins.scala.lang.psi.stubs.index.{ScDirectInheritorsIndex, ScAnnotatedMemberIndex}
import api.toplevel.templates.ScExtendsBlock
import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.{PsiElement, PsiClass}
import elements.ScTemplateDefinitionElementType
import java.util.ArrayList
import psi.impl.toplevel.templates.ScExtendsBlockImpl
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.openapi.diagnostic.Logger
import api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition, ScMember}
import collection.mutable.{HashSet, ArrayBuffer}
import api.expr.ScAnnotation
import com.intellij.psi.util.{PsiUtilCore, PsiUtilBase, PsiUtil}

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaStubsUtil {
  def getClassInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.getName()
    if (name == null) return Seq.empty
    val inheritors = new ArrayBuffer[ScTemplateDefinition]
    val iterator: java.util.Iterator[PsiElement] =
      StubIndex.getInstance().get(ScDirectInheritorsIndex.KEY, name, clazz.getProject(), scope).iterator.
              asInstanceOf[java.util.Iterator[PsiElement]]
    while (iterator.hasNext) {
      val extendsBlock: PsiElement = iterator.next
      if (checkPsiForExtendsBlock(extendsBlock)) {
        val stub = extendsBlock.asInstanceOf[ScExtendsBlockImpl].getStub
        if (stub != null) {
          if (stub.getParentStub.getStubType.isInstanceOf[ScTemplateDefinitionElementType[_ <: ScTemplateDefinition]]) {
            inheritors += stub.getParentStub.getPsi.asInstanceOf[ScTemplateDefinition]
          }
        }
        else {
          extendsBlock.getParent match {
            case tp: ScTemplateDefinition => inheritors += tp
            case _ =>
          }
        }
      }
    }
    inheritors.toSeq
  }

  def checkPsiForExtendsBlock(element: PsiElement): Boolean = {
    element match {
      case x: ScExtendsBlockImpl => return true
      case _ => {
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
        LOG.error("Wrong Psi in Psi list: " + faultyContainer)
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        return false
      }
    }
  }

  def checkPsiForAnnotation(element: PsiElement): Boolean = {
    element match {
      case x: ScAnnotation => return true
      case _ => {
        val faultyContainer = PsiUtilCore.getVirtualFile(element)
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