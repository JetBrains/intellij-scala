package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch.SearchParameters
import com.intellij.psi.search.{LocalSearchScope, PsiSearchScopeUtil, SearchScope}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

/**
  * @see [[ScalaDirectClassInheritorsSearcher]]<br>
  * TODO: show inheritors via gutter doesn't work in Scratch files (see IDEA-313012)
  */
class ScalaLocalInheritorsSearcher extends QueryExecutorBase[PsiClass, ClassInheritorsSearch.SearchParameters] {

  override def processQuery(params: SearchParameters, consumer: Processor[_ >: PsiClass]): Unit = {
    val clazz = params.getClassToProcess

    val scope = params.getScope
    val (_, virtualFiles) = scope match {
      case local: LocalSearchScope if clazz.isInstanceOf[ScalaPsiElement] =>
        (local, local.getVirtualFiles)
      case _ =>
        return
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
                if (td.isInheritor(clazz, true) && checkCandidate(td, params, scope))
                  continue = consumer.process(td)
              case _ =>
            }
          }
        }
      }
    }
  }

  private def checkCandidate(
    candidate: PsiClass,
    parameters: ClassInheritorsSearch.SearchParameters,
    searchScope: SearchScope
  ): Boolean = {
    ProgressManager.checkCanceled()
    if (!PsiSearchScopeUtil.isInScope(searchScope, candidate))
      false
    else candidate match {
      case _: ScNewTemplateDefinition => true
      case td: ScTypeDefinition =>
        parameters.getNameCondition.value(td.name)
    }
  }
}
