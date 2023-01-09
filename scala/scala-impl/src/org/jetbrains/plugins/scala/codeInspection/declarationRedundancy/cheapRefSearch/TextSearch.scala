package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.{GlobalSearchScope, PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.ShouldProcess
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.{Method, SearchMethodResult}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScInterpolatedStringLiteralImpl
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsJava}

private[cheapRefSearch] final class TextSearch(
  override val shouldProcess: ShouldProcess,
  project: Project
) extends Method {

  private val psiSearchHelper: PsiSearchHelper = PsiSearchHelper.getInstance(project)

  override def searchForUsages(ctx: Search.Context): SearchMethodResult = {

    val buffer = new ConcurrentLinkedQueue[ElementUsage]()

    var didExitBeforeExhaustion = false

    val psiElement = ctx.element

    val filesWithMacros = FilesWithMacrosIndexer.filesWithMacros(psiElement.getProject).map { path =>
      VirtualFileManager.getInstance().findFileByNioPath(Paths.get(path))
    }

    val processor = new TextOccurenceProcessor {

      override def execute(e2: PsiElement, offsetInElement: Int): Boolean = {

        val e2ContainingFile = e2.getContainingFile

        if (psiElement.getContainingFile == e2ContainingFile) {
          true
        } else {

          val maybeUsage = e2 match {
            case r: PsiReference =>
              Some(ElementUsageWithKnownReference(r, psiElement))
            case i: ScInterpolatedStringLiteralImpl if filesWithMacros.contains(e2ContainingFile.getVirtualFile) =>
              Some(ElementUsageWithKnownReference(i, psiElement))
            case _ =>
              None
          }

          val continue = maybeUsage.forall { usage =>
            buffer.add(usage)
            !ctx.canExit(usage)
          }

          if (!continue) didExitBeforeExhaustion = true
          continue
        }
      }
    }

    val useScope = psiSearchHelper.getUseScope(psiElement)

    val totalScope = useScope.union(GlobalSearchScope.filesScope(psiElement.getProject, filesWithMacros.asJavaCollection))

    val stringsToSearch = ScalaUsageNamesUtil.getStringsToSearch(psiElement).asScala.toSeq
    stringsToSearch.foreach { name =>
      psiSearchHelper.processElementsWithWord(processor, totalScope, name,
        (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort, true)
    }

    new SearchMethodResult(buffer.asScala.toSeq, didExitBeforeExhaustion)
  }
}
