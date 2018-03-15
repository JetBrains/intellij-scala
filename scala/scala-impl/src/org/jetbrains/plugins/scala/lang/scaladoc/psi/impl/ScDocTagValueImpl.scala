package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import scala.collection.Set
import scala.collection.mutable.ArrayBuilder

import com.intellij.lang.ASTNode
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceElementImpl
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocReferenceElement, ScDocTag, ScDocTagValue}

/**
 * User: Dmitry Naydanov
 * Date: 11/23/11
 */

class ScDocTagValueImpl(node: ASTNode) extends ScReferenceElementImpl(node) with ScDocTagValue with ScDocReferenceElement {
  def nameId: PsiElement = this

  override def getName: String = getText

  def qualifier: Option[ScalaPsiElement] = None

  def getKinds(incomplete: Boolean, completion: Boolean): Set[ResolveTargets.Value] = Set(ResolveTargets.VAL)

  def getSameNameVariants: Array[ScalaResolveResult] = Array.empty

  def multiResolveScala(incompleteCode: Boolean): Array[ScalaResolveResult] =
    getParametersVariants.filter(a =>
      ScalaNamesUtil.equivalent(a.name, refName)).
            map(new ScalaResolveResult(_))

  override def toString: String = "ScalaDocTagValue: " + getText

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

  override def isReferenceTo(element: PsiElement): Boolean = {
    if (resolve() == null || resolve() != element) false else true
  }

  override def handleElementRename(newElementName: String): PsiElement = {
    if (!isIdentifier(newElementName)) return this
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
    import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing.{PARAM_TAG, TYPE_PARAM_TAG}
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