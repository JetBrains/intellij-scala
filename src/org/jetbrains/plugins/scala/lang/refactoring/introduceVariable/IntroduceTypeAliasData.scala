package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
 * Created by Kate Ustyuzhanina
 * on 9/7/15
 */
class IntroduceTypeAliasData {
  var currentScope: ScopeItem = null
  var initialTypeElement: TextRange = null
  var possibleScopes: Array[ScopeItem] = null
  var typeAliasInfo: (PsiFile, TextRange) = null
  var isCallModalDialogInProgress: Boolean = false

  def setTypeAlias(inTypeAlias: ScTypeAlias) = {
    if (inTypeAlias != null) {
      typeAliasInfo = (inTypeAlias.getContainingFile, inTypeAlias.getTextRange)
    }
  }

  def clearData(): Unit = {
    currentScope = null
    initialTypeElement = null
    possibleScopes = null
    typeAliasInfo = null
    isCallModalDialogInProgress = false
  }

  def isData: Boolean = {
    currentScope != null || initialTypeElement != null || possibleScopes != null || typeAliasInfo != null
  }

  def addScopeElement(item: ScopeItem): Unit = {
    currentScope = item
  }

  def setInintialInfo(textRange: TextRange): Unit = {
    if (initialTypeElement == null) {
      initialTypeElement = textRange
    }
  }

  def setPossibleScopes(inPossibleScopes: Array[ScopeItem]): Unit = {
    possibleScopes = inPossibleScopes
  }

  def getNamedElement: ScTypeAlias = {
    val element = PsiTreeUtil.findElementOfClassAtOffset(typeAliasInfo._1,
      typeAliasInfo._2.getStartOffset, classOf[ScTypeAlias], false)

    element match {
      case typeAlias: ScTypeAlias =>
        typeAlias
      case _ => null
    }
  }
}
