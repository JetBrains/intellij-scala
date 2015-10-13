package org.jetbrains.plugins.scala
package lang
package psi
package impl
package search


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

class ScalaDirectClassInheritorsSearcher extends QueryExecutor[PsiClass, DirectClassInheritorsSearch.SearchParameters] {
  def execute(queryParameters: DirectClassInheritorsSearch.SearchParameters, consumer: Processor[PsiClass]): Boolean = {
    val clazz = queryParameters.getClassToProcess
    val scope = queryParameters.getScope match {case x: GlobalSearchScope => x case _ => return true}
    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        if (!clazz.isValid) return true
          val candidates: Seq[ScTemplateDefinition] = ScalaStubsUtil.getClassInheritors(clazz, scope)
          for (candidate <- candidates if candidate.showAsInheritor) {
            ProgressManager.checkCanceled()
            if (candidate.isInheritor(clazz, deep = false)) {
              if (!consumer.process(candidate)) {
                return false
              }
            }
          }
          true
        }
      })
  }
}