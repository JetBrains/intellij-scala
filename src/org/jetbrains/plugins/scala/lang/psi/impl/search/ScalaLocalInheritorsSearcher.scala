package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch.SearchParameters
import com.intellij.psi.search.{LocalSearchScope, PsiSearchScopeUtil, SearchScope}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

/**
  * @author Nikolay.Tropin
  */
class ScalaLocalInheritorsSearcher extends QueryExecutorBase[PsiClass, ClassInheritorsSearch.SearchParameters] {
  override def processQuery(params: SearchParameters, consumer: Processor[PsiClass]): Unit = {
    val clazz = params.getClassToProcess

    val (_, virtualFiles) = params.getScope match {
      case local: LocalSearchScope if clazz.isInstanceOf[ScalaPsiElement] => (local, local.getVirtualFiles)
      case _ => return
    }

    val project = clazz.getProject

    for (virtualFile <- virtualFiles) {
      ProgressManager.checkCanceled()
      var continue = true
      inReadAction {
        if (continue) {
          val psiFile: PsiFile = PsiManager.getInstance(project).findFile(virtualFile)
          if (psiFile != null) {
            psiFile.depthFirst().foreach {
              case td: ScTemplateDefinition if continue =>
                if (td.isInheritor(clazz, deep = true) && checkCandidate(td, params))
                  continue = consumer.process(td)
              case _ =>
            }
          }
        }
      }
    }

  }

  private def checkCandidate(candidate: PsiClass, parameters: ClassInheritorsSearch.SearchParameters): Boolean = {
    val searchScope: SearchScope = parameters.getScope
    ProgressManager.checkCanceled()
    if (!PsiSearchScopeUtil.isInScope(searchScope, candidate)) false
    else candidate match {
      case _: ScNewTemplateDefinition => true
      case td: ScTypeDefinition => parameters.getNameCondition.value(td.name)
    }
  }
}
