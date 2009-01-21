package org.jetbrains.plugins.scala.lang.parameterInfo

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.lang.parameterInfo._

import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiMethod, PsiNamedElement}
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.hash.HashSet
import java.lang.{Class, String}
import java.util.Set
import lexer.ScalaTokenTypes
import psi.api.base.ScConstructor
import psi.api.expr.{ScArgumentExprList, ScReferenceExpression, ScMethodCall, ScExpression}
import psi.api.statements.ScFunction
import psi.ScalaPsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.01.2009
 */

class ScalaFunctionParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[ScArgumentExprList, Any, ScExpression] {
  def getParameterCloseChars: String = "{},);\n"

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
    findCall(context)
  }

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ScArgumentExprList = {
    findCall(context)
  }

  def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = {
    p match {
      case x: ScFunction => {
        x.parameters.toArray
      }
      case _ => ArrayUtil.EMPTY_OBJECT_ARRAY
    }
  }

  def showParameterInfo(element: ScArgumentExprList, context: CreateParameterInfoContext): Unit = {
    context.showHint(element, element.getTextRange.getStartOffset, this)
  }

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = {
    val allElements = JavaCompletionUtil.getAllPsiElements(item.asInstanceOf[LookupItem[_]])

    if (allElements != null &&
        allElements.size > 0 &&
        allElements.get(0).isInstanceOf[PsiMethod]) {
      return allElements.toArray(new Array[Object](allElements.size));
    }
    return null
  }

  def updateParameterInfo(o: ScArgumentExprList, context: UpdateParameterInfoContext): Unit = {
    /*val index = context.getOffset
    context.setCurrentParameter(index)*/
  }

  def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
    /*context.setUIComponentEnabled(true)
    context.setupUIComponentPresentation("texting", 0, 4,false, false,false, context.getDefaultParameterColor)*/
  }

  def tracksParameterIndex: Boolean = true

  private def findCall(context: ParameterInfoContext): ScArgumentExprList = {
    val (file, offset) = (context.getFile, context.getOffset)
    val element = file.findElementAt(offset)
    if (element == null) return null
    val args = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    if (args != null) {
      context match {
        case context: CreateParameterInfoContext => {
          args.getParent match {
            case call: ScMethodCall => {
              call.getInvokedExpr match {
                case ref: ScReferenceExpression => {
                  val name = ref.refName
                  val variants = ref.getSameNameVariants
                  context.setItemsToShow(variants)
                }
                case _ =>
              }
            }
            case _ =>
          }
        }
        case _ =>
      }
    }
    return args
  }
}