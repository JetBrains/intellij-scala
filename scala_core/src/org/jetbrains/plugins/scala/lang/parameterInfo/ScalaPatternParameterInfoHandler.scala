package org.jetbrains.plugins.scala.lang.parameterInfo


import _root_.java.lang.{Class, String}
import _root_.scala.collection.mutable.HashSet
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.psi.PsiMethod
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.psi.tree.IElementType
import _root_.java.util.Set
import com.intellij.lang.parameterInfo._



import com.intellij.util.ArrayUtil
import lexer.ScalaTokenTypes
import psi.api.base.patterns.{ScPattern, ScConstructorPattern, ScPatternArgumentList}
import psi.api.base.ScConstructor
import psi.api.expr.{ScArgumentExprList, ScMethodCall, ScExpression}
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaPatternParameterInfoHandler/*todo extends ParameterInfoHandlerWithTabActionSupport[ScPatternArgumentList, Any, ScPattern]*/ {
  def getParameterCloseChars: String = "{},);\n"

  def couldShowInLookup: Boolean = true

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(patternArgumentList: ScPatternArgumentList): Array[ScPattern] = patternArgumentList.patterns.toArray

  def getArgumentListClass: Class[ScPatternArgumentList] = classOf[ScPatternArgumentList]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

 /* def getArgumentListAllowedParentClasses: Set[Class[_]] = {
    val set = new HashSet[Class[_]]()
    set.add(classOf[ScConstructorPattern])
    set
  }

  def findElementForParameterInfo(context: CreateParameterInfoContext): ScPatternArgumentList = {
    findCall(context)
  }

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ScPatternArgumentList = {
    findCall(context)
  }*/

  def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = ArrayUtil.EMPTY_OBJECT_ARRAY

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = {
    val allElements = JavaCompletionUtil.getAllPsiElements(item.asInstanceOf[LookupItem[_]])

    if (allElements != null &&
        allElements.size > 0 &&
        allElements.get(0).isInstanceOf[PsiMethod]) {
      return allElements.toArray(new Array[Object](allElements.size));
    }
    return null
  }
}