package org.jetbrains.plugins.scala.lang.psi.impl.search

import _root_.scala.collection.mutable.ArrayBuffer
import api.statements.ScFunction
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.{PsiMember, PsiMethod, PsiElement, PsiNamedElement}
import com.intellij.util.{QueryExecutor, Processor}
/**
 * User: Alexander Podkhalyuzin
 * Date: 16.12.2008
 */

class MethodImplementationsSearch extends QueryExecutor[PsiElement, PsiElement] {
  override def execute(sourceElement: PsiElement, consumer: Processor[PsiElement]): Boolean = {
    if (sourceElement.isInstanceOf[ScFunction]) {
      for (implementation <- getMethodImplementations(sourceElement.asInstanceOf[PsiMethod])) {
        if ( !consumer.process(implementation) ) {
          return false
        }
      }
    }
    return true;
  }

  def getOverridingMethods(method: PsiMethod, list: ArrayBuffer[PsiNamedElement]) {
    for (psiMethod <- ScalaOverridengMemberSearch.search(method, true)) {
      list += psiMethod
    }
  }

  def getMethodImplementations(method: PsiMethod): Array[PsiNamedElement] = {
    val result = new ArrayBuffer[PsiNamedElement]

    getOverridingMethods(method, result)
    return result.toArray
  }
}