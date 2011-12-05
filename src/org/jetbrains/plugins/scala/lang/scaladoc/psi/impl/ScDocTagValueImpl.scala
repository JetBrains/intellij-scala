package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import java.lang.String
import com.intellij.openapi.util.TextRange
import parser.parsing.MyScaladocParsing
import lang.psi.api.toplevel.ScNamedElement
import lang.psi.api.statements.ScFunction
import lang.psi.api.base.ScPrimaryConstructor
import api.{ScDocReferenceElement, ScDocComment, ScDocTag, ScDocTagValue}
import collection.mutable.ArrayBuilder
import com.intellij.openapi.fileEditor.FileDocumentManager
import lang.psi.api.statements.params.{ScTypeParam, ScParameter, ScTypeParamClause}
import collection.Set
import lang.psi.{ScalaPsiElement, ScalaPsiElementImpl}
import resolve.{ScalaResolveResult, StdKinds, ResolveTargets}
import refactoring.util.ScalaNamesUtil
import com.intellij.psi.{PsiDocumentManager, ResolveResult, PsiReference, PsiElement}

/**
 * User: Dmitry Naydanov
 * Date: 11/23/11
 */

class ScDocTagValueImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocTagValue with ScDocReferenceElement {
  def nameId: PsiElement = this

  def qualifier: Option[ScalaPsiElement] = None

  def getKinds(incomplete: Boolean, completion: Boolean): Set[ResolveTargets.Value] = Set(ResolveTargets.VAL)

  def getSameNameVariants: Array[ResolveResult] = Array.empty

  def multiResolve(incompleteCode: Boolean): Array[ResolveResult] =
    getParametersVariants.filter(_.getName == refName).map(new ScalaResolveResult(_))

  override def toString = "ScalaDocTagValue"

  def getValue: String = getText

//  def getElement: PsiElement = getFirstChild
  
  def bindToElement(element: PsiElement): PsiElement = {
    element match {
      case _ : ScParameter => this
      case _ : ScTypeParam => this
      case _ => throw new UnsupportedOperationException("Can't bind to this element")
    }
  }

  override  def getCanonicalText: String = if (getFirstChild == null) null else getFirstChild.getText

  override def isReferenceTo(element: PsiElement) = {
    if (resolve() == null || resolve() != element) false else true
  }    

  override def handleElementRename(newElementName: String): PsiElement = {
    if (!ScalaNamesUtil.isIdentifier(newElementName)) return this
    val doc = FileDocumentManager.getInstance().getDocument(getContainingFile.getVirtualFile)
    val range: TextRange = getFirstChild.getTextRange
    doc.replaceString(range.getStartOffset, range.getEndOffset, newElementName)
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    getElement
  }

  def getVariants: Array[AnyRef] = {
    val result = ArrayBuilder.make[AnyRef]()
    val parameters = getParametersVariants
    if (parameters == null) {
      return Array[AnyRef]()
    }
    parameters.foreach(result += _.getName)
    result.result()
  }
  
  def getParametersVariants: Array[ScNamedElement] = {
    import MyScaladocParsing.{PARAM_TAG, TYPE_PARAM_TAG}
    val parentTagType: String = getParent match {
      case a: ScDocTag => a.getName
      case _ => null
    }
    var parent = getParent
    while (parent != null && !parent.isInstanceOf[ScDocComment]) {
      parent = parent.getParent
    }

    if (parent == null || (parentTagType != PARAM_TAG && parentTagType != TYPE_PARAM_TAG))
      return Array.empty[ScNamedElement]
    val sibl = parent.asInstanceOf[ScDocComment].getNextSiblingNotWhitespace

    def filterParamsByName(tagName: String, params: Seq[ScNamedElement]): Array[ScNamedElement] = {
      val paramsSet =
        (for (tag <- parent.asInstanceOf[ScDocComment].findTagsByName(tagName) if tag.getValueElement != null &&
                tag != getParent)
        yield tag.getValueElement.getText).toSet

      val result = ArrayBuilder.make[ScNamedElement]()
      params.filter(param => !paramsSet.contains(param.getName)).foreach(result += _)
      result.result()
    }

    sibl match {
      case func: ScFunction =>
        if (parentTagType == PARAM_TAG) {
          filterParamsByName(PARAM_TAG, func.parameters)
        } else {
          filterParamsByName(TYPE_PARAM_TAG, func.typeParameters)
        }
      case constr: ScPrimaryConstructor =>
        if (parentTagType == PARAM_TAG) {
          filterParamsByName(PARAM_TAG, constr.parameters)
        } else {
          constr.getClassTypeParameters match {
            case a: Some[ScTypeParamClause] =>
              filterParamsByName(TYPE_PARAM_TAG, a.get.typeParameters)
            case _ => Array[ScNamedElement]()
          }
        }
      case _ => Array.empty[ScNamedElement]
    }
  }
}