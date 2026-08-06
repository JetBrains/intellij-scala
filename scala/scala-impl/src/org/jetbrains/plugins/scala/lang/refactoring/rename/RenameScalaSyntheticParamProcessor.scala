package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

import java.util

class RenameScalaSyntheticParamProcessor extends RenamePsiElementProcessor with ScalaRenameProcessor {
  override def canProcessElement(element: PsiElement): Boolean = realParamForSyntheticParam(element).isDefined

  override def findReferences(element: PsiElement,
                              searchScope: SearchScope,
                              searchInCommentsAndStrings: Boolean): util.Collection[PsiReference] =
    super[RenamePsiElementProcessor].findReferences(element, searchScope, searchInCommentsAndStrings)

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = realParamForSyntheticParam(element).orNull

  private def realParamForSyntheticParam(element: PsiElement) = element match {
    case x: ScParameter => ScalaPsiUtil.parameterForSyntheticParameter(x)
    case _ => None
  }
}