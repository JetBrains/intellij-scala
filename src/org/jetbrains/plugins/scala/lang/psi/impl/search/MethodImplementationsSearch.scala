package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search

import collection.mutable.ArrayBuffer
import api.toplevel.ScNamedElement
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.{QueryExecutor, Processor}
import extensions.inReadAction

/**
 * User: Alexander Podkhalyuzin
 * Date: 16.12.2008
 */
class MethodImplementationsSearch extends QueryExecutor[PsiElement, PsiElement] {
  override def execute(sourceElement: PsiElement, consumer: Processor[PsiElement]): Boolean = {
    if (sourceElement.isInstanceOf[ScNamedElement]) {
      for (implementation <- getOverridingMethods(sourceElement.asInstanceOf[ScNamedElement])) {
        if (!consumer.process(implementation)) {
          return false
        }
      }
    }
    true
  }

  def getOverridingMethods(method: ScNamedElement): Array[PsiNamedElement] = {
    val result = new ArrayBuffer[PsiNamedElement]
    inReadAction {
      for (psiMethod <- ScalaOverridengMemberSearch.search(method, true)) {
        result += psiMethod
      }
    }
    result.toArray
  }
}