package org.jetbrains.plugins.scala
package findUsages

import com.intellij.openapi.application.ReadActionProcessor
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.cache.impl.id.{IdIndex, IdIndexEntry}
import com.intellij.psi.impl.search.PsiSearchHelperImpl
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{GlobalSearchScope, PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiElement, PsiManager, PsiReference}
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.{CommonProcessors, Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.{isBackticked, isOpCharacter}
import scala.collection.JavaConverters._

/**
  * Nikolay.Tropin
  * 9/10/13
  */
class OperatorAndBacktickedSearcher extends QueryExecutor[PsiReference, ReferencesSearch.SearchParameters] {
  def execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor[PsiReference]): Boolean = {
    val project = queryParameters.getProject
    val scope = inReadAction(ScalaFilterScope(queryParameters))
    val element = queryParameters.getElementToSearch
    val manager = PsiManager.getInstance(project)

    val toProcess: Seq[(PsiElement, String)] = inReadAction {
      element match {
        case e if !e.isValid => Nil
        case isBackticked(name) => if (name != "") Seq((element, name), (element, s"`$name`")) else Seq((element, "``"))
        case named: ScNamedElement if named.name.exists(isOpCharacter) => Seq((named, named.name))
        case _ => Nil
      }
    }
    toProcess.foreach { case (elem, name) =>
      val processor: TextOccurenceProcessor = new TextOccurenceProcessor {
        def execute(element: PsiElement, offsetInElement: Int): Boolean = {
          val references = inReadAction(element.getReferences)
          for (ref <- references if ref.getRangeInElement.contains(offsetInElement)) {
            inReadAction {
              if (ref.isReferenceTo(elem) || ref.resolve() == elem) {
                if (!consumer.process(ref)) return false
              }
            }
          }
          true
        }
      }
      val helper: PsiSearchHelper = new ScalaPsiSearchHelper(manager.asInstanceOf[PsiManagerEx])
      try {
        helper.processElementsWithWord(processor, scope, name, UsageSearchContext.IN_CODE, true)
      }
      catch {
        case _: IndexNotReadyException =>
      }
    }
    true
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
      inReadAction {
        FileBasedIndex.getInstance.processFilesContainingAllKeys(IdIndex.NAME, entries.asJava, scope, checker, collectProcessor)
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
    private def getWordEntries(name: String, caseSensitively: Boolean): Seq[IdIndexEntry] =
      if (isIdentifier(name)) Seq(new IdIndexEntry(name, caseSensitively)) else Seq.empty
  }

}
