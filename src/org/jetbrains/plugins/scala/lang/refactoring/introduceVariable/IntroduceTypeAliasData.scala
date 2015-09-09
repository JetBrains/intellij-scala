package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.util

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.refactoring.scopeSuggester.ScopeItem
import scala.collection.mutable.ArrayBuffer

/**
 * Created by Kate Ustyuzhanina on 9/7/15.
 */
object IntroduceTypeAliasData {
  val scopeElements: ArrayBuffer[ScopeItem] = new ArrayBuffer[ScopeItem]()
  var initialInfo: (String, Int) = ("", 0)
  var possibleScopes: util.ArrayList[ScopeItem] = null
  var isReverted = false

  def clearData(): Unit = {
    scopeElements.clear()
    initialInfo = ("", 0)
    possibleScopes = null
    isReverted = false
  }

  def isData:Boolean = {
    scopeElements.length != 0 || initialInfo != ("", 0) || possibleScopes != null
  }

  def addScopeElement(item: ScopeItem): Unit = {
    scopeElements += item
  }

  def setInintialInfo(inText: String, caretPosition: Int): Unit = {
    if (initialInfo._1 == "") {
      initialInfo = (inText, caretPosition)
    }
  }

  def setPossibleScopes(inPossibleScopes: util.ArrayList[ScopeItem]): Unit = {
    possibleScopes = inPossibleScopes
  }

  def getNamedElement: ScTypeAlias = {
    val element = PsiTreeUtil.findElementOfClassAtOffset(scopeElements.last.typeAliasFile,
      scopeElements.last.typeAliasOffset.getStartOffset, classOf[ScTypeAlias], false)
    if (element != null) {
      element.asInstanceOf[ScTypeAlias]
    }
    element
  }

  def revertToInitial = scopeElements.size > 1

}
