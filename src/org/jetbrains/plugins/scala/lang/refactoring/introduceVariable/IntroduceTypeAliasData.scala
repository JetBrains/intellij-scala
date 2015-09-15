package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.refactoring.scopeSuggester.ScopeItem

/**
 * Created by Kate Ustyuzhanina on 9/7/15.
 */
object IntroduceTypeAliasData {
  var currentScope: ScopeItem = null
  var initialInfo: (String, Int) = ("", 0)
  var possibleScopes: Array[ScopeItem] = null
  var isReverted = false
  var typeElementRanges:TextRange = null

  def clearData(): Unit = {
    currentScope = null
    initialInfo = ("", 0)
    possibleScopes = null
    isReverted = false
  }

  def isData:Boolean = {
    currentScope != null || initialInfo != ("", 0) || possibleScopes != null
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

  def getNamedElement: ScTypeAlias = {
    val element = PsiTreeUtil.findElementOfClassAtOffset(currentScope.typeAliasFile,
      currentScope.typeAliasOffset.getStartOffset, classOf[ScTypeAlias], false)
    if (element != null) {
      element.asInstanceOf[ScTypeAlias]
    }
    element
  }

}
