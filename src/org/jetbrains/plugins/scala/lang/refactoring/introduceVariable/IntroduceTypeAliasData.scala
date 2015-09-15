package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.util

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.refactoring.scopeSuggester.ScopeItem

/**
 * Created by Kate Ustyuzhanina on 9/7/15.
 */
object IntroduceTypeAliasData {
  var currentScope: ScopeItem = null
  var initialInfo: (String, Int) = ("", 0)
  var possibleScopes: Array[ScopeItem] = null
  var typeElementRanges: TextRange = null

  def clearData(): Unit = {
    currentScope = null
    initialInfo = ("", 0)
    possibleScopes = null
  }

  def isData: Boolean = {
    currentScope != null || initialInfo !=("", 0) || possibleScopes != null
  }

  def addScopeElement(item: ScopeItem): Unit = {
    //    val elementCopy = item.copy()
    currentScope = item
  }

  def setInintialInfo(inText: String, caretPosition: Int): Unit = {
    if (initialInfo._1 == "") {
      initialInfo = (inText, caretPosition)
    }
  }

  def setPossibleScopes(inPossibleScopes: Array[ScopeItem]): Unit = {
    possibleScopes = inPossibleScopes
  }

  def getTypeElement(file: PsiFile): ScTypeElement ={
    PsiTreeUtil.findElementOfClassAtRange(file, typeElementRanges.getStartOffset, typeElementRanges.getEndOffset, classOf[ScTypeElement])
  }

  def getNamedElement: ScTypeAlias = {
    val element = PsiTreeUtil.findElementOfClassAtOffset(currentScope.typeAliasFile,
      currentScope.typeAliasOffset.getStartOffset, classOf[ScTypeAlias], false)
    if (element != null) {
      element.asInstanceOf[ScTypeAlias]
    }
    element
  }

}
