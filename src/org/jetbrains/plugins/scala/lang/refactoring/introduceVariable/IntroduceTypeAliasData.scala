package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
 * Created by Kate Ustyuzhanina
 * on 9/7/15
 */
object IntroduceTypeAliasData {
  var currentScope: ScopeItem = null
  var initialInfo: (String, TextRange) = null
  var possibleScopes: Array[ScopeItem] = null
  var typeAliasInfo: (PsiFile, TextRange) = null

  def setTypeAlias(inTypeAlias: ScTypeAlias) = {
    if (inTypeAlias != null) {
      typeAliasInfo = (inTypeAlias.getContainingFile, inTypeAlias.getTextRange)
    }
  }

  def clearData(): Unit = {
    currentScope = null
    initialInfo = null
    possibleScopes = null
    typeAliasInfo = null
  }

  def isData: Boolean = {
    currentScope != null || initialInfo != null || possibleScopes != null || typeAliasInfo != null
  }

  def addScopeElement(item: ScopeItem): Unit = {
    currentScope = item
  }

  def setInintialInfo(inText: String, textRange: TextRange): Unit = {
    if (initialInfo == null) {
      initialInfo = (inText, textRange)
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
