package org.jetbrains.plugins.scala.lang.parameterInfo

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.lang.parameterInfo._

import com.intellij.codeInsight.lookup.LookupElement


import com.intellij.psi.PsiMethod
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.hash.HashSet
import java.lang.{Class, String}
import java.util.Set
import lexer.ScalaTokenTypes
import psi.api.base.ScConstructor
import psi.api.expr.{ScArgumentExprList, ScMethodCall, ScExpression}
import psi.api.statements.ScFunction
import psi.ScalaPsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.01.2009
 */

class ScalaFunctionParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[ScArgumentExprList, Any, ScExpression] {
  def getParameterCloseChars: String = ",)"

  def couldShowInLookup: Boolean = true

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(argExprList: ScArgumentExprList): Array[ScExpression] = argExprList.exprs.toArray

  def getArgumentListClass: Class[ScArgumentExprList] = classOf[ScArgumentExprList]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRPARENTHESIS

  def getArgumentListAllowedParentClasses: Set[Class[_]] = {
    val set = new HashSet[Class[_]]()
    set.add(classOf[ScMethodCall])
    set.add(classOf[ScConstructor])
    set
  }

  def findElementForParameterInfo(context: CreateParameterInfoContext): ScArgumentExprList = {
    val (file, offset, parameterStart) = (context.getFile, context.getOffset, context.getParameterListStart)
    val element = file.findElementAt(offset)
    if (element == null) return null
    val args = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    return args
  }

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ScArgumentExprList = {
    val (file, offset, parameterStart) = (context.getFile, context.getOffset, context.getParameterListStart)
    val element = file.findElementAt(offset)
    if (element == null) return null
    val args = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    return args
  }

  def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = {
    p match {
      case x: ScFunction => {
        x.parameters.toArray
      }
      case _ => ArrayUtil.EMPTY_OBJECT_ARRAY
    }
  }

  def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {}

  def showParameterInfo(element: ScArgumentExprList, context: CreateParameterInfoContext): Unit = {}

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = null

  def updateParameterInfo(o: ScArgumentExprList, context: UpdateParameterInfoContext): Unit = {}

  def tracksParameterIndex: Boolean = false
}