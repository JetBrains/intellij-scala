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
import lang.psi.api.base.ScPrimaryConstructor
import api.{ScDocReferenceElement, ScDocComment, ScDocTag, ScDocTagValue}
import collection.mutable.ArrayBuilder
import com.intellij.openapi.fileEditor.FileDocumentManager
import lang.psi.api.statements.params.{ScTypeParam, ScParameter, ScTypeParamClause}
import collection.Set
import resolve.{ScalaResolveResult, ResolveTargets}
import refactoring.util.ScalaNamesUtil
import com.intellij.psi.{PsiDocumentManager, ResolveResult, PsiElement}
import lang.psi.api.statements.{ScTypeAlias, ScFunction}
import lang.psi.api.toplevel.typedef.{ScTrait, ScClass}
import lang.psi.{ScalaPsiUtil, ScalaPsiElement, ScalaPsiElementImpl}
import extensions.toPsiNamedElementExt
import lang.completion.lookups.ScalaLookupItem

/**
 * User: Dmitry Naydanov
 * Date: 11/23/11
 */

class ScDocTagValueImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocTagValue with ScDocReferenceElement {
  def nameId: PsiElement = this

  override def getName = getText

  def qualifier: Option[ScalaPsiElement] = None

  def getKinds(incomplete: Boolean, completion: Boolean): Set[ResolveTargets.Value] = Set(ResolveTargets.VAL)

  def getSameNameVariants: Array[ResolveResult] = Array.empty

  def multiResolve(incompleteCode: Boolean): Array[ResolveResult] =
    getParametersVariants.filter(a =>
      a.name == refName || ScalaPsiUtil.convertMemberName(a.name) == ScalaPsiUtil.convertMemberName(refName)).
            map(new ScalaResolveResult(_))

  override def toString = "ScalaDocTagValue"

  def getValue: String = getText

  def bindToElement(element: PsiElement): PsiElement = {
    element match {
      case _ : ScParameter => this
      case _ : ScTypeParam =>
        handleElementRename(element.getText)
        this
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
    PsiDocumentManager.getInstance(getProject).doPostponedOperationsAndUnblockDocument(doc)
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
    parameters.foreach {
      param =>
        result += new ScalaLookupItem(param, param.name, None)
    }
    result.result()
  }


  override def isSoft: Boolean = getParent.asInstanceOf[ScDocTag].name == MyScaladocParsing.THROWS_TAG

  def getParametersVariants: Array[ScNamedElement] = {
    import MyScaladocParsing.{PARAM_TAG, TYPE_PARAM_TAG}
    val parentTagType: String = getParent match {
      case a: ScDocTag => a.name
      case _ => null
    }
    var parent = getParent
    while (parent != null && !parent.isInstanceOf[ScDocComment]) {
      parent = parent.getParent
    }

    if (parent == null || (parentTagType != PARAM_TAG && parentTagType != TYPE_PARAM_TAG))
      return Array.empty[ScNamedElement]

    def filterParamsByName(tagName: String, params: Seq[ScNamedElement]): Array[ScNamedElement] = {
      val paramsSet =
        (for (tag <- parent.asInstanceOf[ScDocComment].findTagsByName(tagName) if tag.getValueElement != null &&
                tag != getParent)
        yield tag.getValueElement.getText).toSet

      val result = ArrayBuilder.make[ScNamedElement]()
      params.filter(param => !paramsSet.contains(param.name)).foreach(result += _)
      result.result()
    }

    parent.getParent match {
      case func: ScFunction =>
        if (parentTagType == PARAM_TAG) {
          filterParamsByName(PARAM_TAG, func.parameters)
        } else {
          filterParamsByName(TYPE_PARAM_TAG, func.typeParameters)
        }
      case clazz: ScClass =>
        val constr = clazz.constructor
        
        constr match {
          case primaryConstr: Some[ScPrimaryConstructor] =>
            if (parentTagType == PARAM_TAG) {
              filterParamsByName(PARAM_TAG, primaryConstr.get.parameters)
            } else {
              primaryConstr.get.getClassTypeParameters match {
                case tParam: Some[ScTypeParamClause] =>
                  filterParamsByName(TYPE_PARAM_TAG, tParam.get.typeParameters)
                case _ => Array.empty[ScNamedElement]
              }
            }
          case None => Array.empty[ScNamedElement]
        }
      case traitt: ScTrait => 
        if (parentTagType == TYPE_PARAM_TAG) {
          filterParamsByName(TYPE_PARAM_TAG, traitt.typeParameters)
        } else {
          Array.empty[ScNamedElement]
        }
      case typeAlias: ScTypeAlias =>
        if (parentTagType == TYPE_PARAM_TAG) {
          filterParamsByName(TYPE_PARAM_TAG, typeAlias.typeParameters)
        } else {
          Array.empty[ScNamedElement]
        }
      case _ => Array.empty[ScNamedElement]
    }
  }
}