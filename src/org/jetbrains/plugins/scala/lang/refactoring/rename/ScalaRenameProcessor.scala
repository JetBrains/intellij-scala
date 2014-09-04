package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * Nikolay.Tropin
 * 2014-03-27
 */
trait ScalaRenameProcessor { this: RenamePsiElementProcessor =>

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
