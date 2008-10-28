package org.jetbrains.plugins.scala.lang.psi.impl.search


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.util.{QueryExecutor, Processor}
import stubs.util.ScalaStubsUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

class ScalaDirectClassInheritorsSearcher extends QueryExecutor[PsiClass, DirectClassInheritorsSearch.SearchParameters] {
  def execute(queryParameters: DirectClassInheritorsSearch.SearchParameters, consumer: Processor[PsiClass]): Boolean = {
    val clazz = queryParameters.getClassToProcess
    val scope: GlobalSearchScope = queryParameters.getScope match {case x: GlobalSearchScope => x case _ => return true}
    ApplicationManager.getApplication().runReadAction(new Computable[Boolean] {
        def compute: Boolean = {
          val candidates = ScalaStubsUtil.getClassInheritors(clazz, scope)
          for (candidate <- candidates) {
            if (candidate.isInheritor(clazz, false)) {
              if (!consumer.process(candidate)) return false
            }
          }
          true
        }
      })
  }
}