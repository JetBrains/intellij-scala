package org.jetbrains.plugins.scala
package findUsages

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.{CommonProcessors, QueryExecutor, Processor}
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.psi.search._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.search.PsiSearchHelperImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.Condition
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.util.containers.ContainerUtil
import com.intellij.openapi.application.ReadActionProcessor
import java.util
import com.intellij.psi.impl.cache.impl.id.{IdIndex, IdIndexEntry}
import com.intellij.util.indexing.FileBasedIndex

/**
 * Nikolay.Tropin
 * 9/10/13
 */
class OperatorAndBacktickedSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    extensions.inReadAction {
      val scope = queryParameters.getEffectiveSearchScope
      val element = queryParameters.getElementToSearch
      if (!element.isValid) return true
      val toProcess = element match {
        case ScalaNamesUtil.isBackticked(name) => Seq((element, name), (element, s"`$name`"))
        case named: ScNamedElement if named.name.exists(ScalaNamesUtil.isOpCharacter) => Seq((named, named.name))
        case _ => Nil
      }
      toProcess.foreach { case (elem, name) =>
        val processor = new RequestResultProcessor {
          def processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor[PsiReference]): Boolean = {
            val references = element.getReferences
            for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
              if (ref.isReferenceTo(elem) || ref.resolve() == elem) {
                if (!consumer.process(ref)) return false
              }
            }
            true
          }
        }
        try {
          queryParameters.getOptimizer.searchWord(name, scope, UsageSearchContext.IN_CODE, true, processor)
        } catch {
          case ignore: IndexNotReadyException =>
        }
      }
      true
    }
  }

  private class ScalaPsiSearchHelper(manager: PsiManagerEx) extends PsiSearchHelperImpl(manager) {
    override def processFilesWithText(scope: GlobalSearchScope,
                                      searchContext: Short,
                                      caseSensitively: Boolean,
                                      text: String,
                                      processor: Processor[VirtualFile]): Boolean = {
      val entries = getWordEntries(text, caseSensitively)
      if (entries.isEmpty) return true
      val collectProcessor: CommonProcessors.CollectProcessor[VirtualFile] = new CommonProcessors.CollectProcessor[VirtualFile]
      val checker = new Condition[Integer] {
        def value(integer: Integer): Boolean = (integer.intValue & searchContext) != 0
      }
      extensions.inReadAction {
        FileBasedIndex.getInstance.processFilesContainingAllKeys(IdIndex.NAME, entries, scope, checker, collectProcessor)
      }
      val index: FileIndexFacade = FileIndexFacade.getInstance(manager.getProject)
      ContainerUtil.process(collectProcessor.getResults, new ReadActionProcessor[VirtualFile] {
        def processInReadAction(virtualFile: VirtualFile): Boolean = {
          !index.shouldBeFound(scope, virtualFile) || processor.process(virtualFile)
        }
      })
    }

    /**
     * Only this method is actually differs from PsiSearchHelperImpl,
     * because it works only for java identifiers there.
     */
    private def getWordEntries(name: String, caseSensitively: Boolean): util.List[IdIndexEntry] = {
      val keys = new util.ArrayList[IdIndexEntry]
      if (ScalaNamesUtil.isIdentifier(name)) keys.add(new IdIndexEntry(name, caseSensitively))
      keys
    }
  }
}
