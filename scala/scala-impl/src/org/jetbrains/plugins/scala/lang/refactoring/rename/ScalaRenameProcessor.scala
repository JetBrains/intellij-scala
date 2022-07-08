package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil.isAliased
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.util
import scala.jdk.CollectionConverters._

trait ScalaRenameProcessor { this: RenamePsiElementProcessor =>

  override def findReferences(element: PsiElement,
                              searchScope: SearchScope,
                              searchInCommentsAndStrings: Boolean): util.Collection[PsiReference] = {

    val allRefs = ReferencesSearch.search(element, searchScope.intersectWith(element.getUseScope)).findAll()
    val filtered = allRefs.asScala.filterNot(isAliased).filterNot(ScalaRenameProcessor.isIndirectReference(_, element))
    new util.ArrayList[PsiReference](filtered.asJavaCollection)
  }

  override def setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean): Unit = {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_NON_CODE_FILES = enabled
  }

  override def isToSearchForTextOccurrences(element: PsiElement): Boolean = {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_NON_CODE_FILES
  }

  override def setToSearchInComments(element: PsiElement, enabled: Boolean): Unit = {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_AND_STRINGS = enabled
  }

  override def isToSearchInComments(element: PsiElement): Boolean = {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_AND_STRINGS
  }
}

object ScalaRenameProcessor {
  private def isIndirectReference(ref: PsiReference, element: PsiElement): Boolean = ref match {
    case scRef: ScReference => scRef.isIndirectReferenceTo(ref.resolve(), element)
    case _ => false
  }
}
