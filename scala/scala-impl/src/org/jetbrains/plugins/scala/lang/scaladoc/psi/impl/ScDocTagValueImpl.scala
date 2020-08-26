package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, ResolveState}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceImpl
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing.{PARAM_TAG, TYPE_PARAM_TAG}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocReference, ScDocTag, ScDocTagValue}

import scala.collection.{Set, mutable}

final class ScDocTagValueImpl(node: ASTNode) extends ScReferenceImpl(node) with ScDocTagValue with ScDocReference {

  import ResolveTargets._

  override def nameId: PsiElement = this

  override def getName: String = getText

  override def qualifier: Option[ScalaPsiElement] = None

  override def getKinds(incomplete: Boolean, completion: Boolean): Set[Value] = Set(
    CLASS,
    VAL,
    VAR
  )

  override def getSameNameVariants: Array[ScalaResolveResult] = Array.empty

  override def multiResolveScala(incompleteCode: Boolean): Array[ScalaResolveResult] =
    doResolve(
      new ResolveProcessor(getKinds(incompleteCode), this, refName),
      accessibilityCheck = false
    )

  override def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean): Array[ScalaResolveResult] = {
    if (!accessibilityCheck) processor.doNotCheckAccessibility()

    getParametersVariants.foreach {
      processor.execute(_, ResolveState.initial)
    }
    processor.candidates
  }

  override def toString: String = "ScalaDocTagValue: " + getText

  override def bindToElement(element: PsiElement): PsiElement =
    element match {
      case _: ScParameter => this
      case _: ScTypeParam =>
        handleElementRename(element.getText)
        this
      case _ => throw new UnsupportedOperationException("Can't bind to this element")
    }

  override def getCanonicalText: String = if (getFirstChild == null) null else getFirstChild.getText

  override def isReferenceTo(@NotNull element: PsiElement): Boolean =
    resolve() == element

  override def handleElementRename(newElementName: String): PsiElement = {
    if (!isIdentifier(newElementName)) return this

    val doc = FileDocumentManager.getInstance().getDocument(getContainingFile.getVirtualFile)
    PsiDocumentManager.getInstance(getProject).doPostponedOperationsAndUnblockDocument(doc)
    val range: TextRange = getFirstChild.getTextRange
    doc.replaceString(range.getStartOffset, range.getEndOffset, newElementName)
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    getElement
  }

  override def completionVariants(withImplicitConversions: Boolean): Array[ScalaResolveResult] =
    getParametersVariants.map { element =>
      new ScalaResolveResult(element)
    }

  override def isSoft: Boolean = !isParamTag

  private def parentTagName: String =
    getParent.asOptionOf[ScDocTag].flatMap(_.getName.toOption).getOrElse("")

  private def isParamTag: Boolean = parentTagName match {
    case PARAM_TAG | TYPE_PARAM_TAG => true
    case _ => false
  }

  private def getParametersVariants: Array[ScNamedElement] = {
    val parentTagType = parentTagName
    val scalaDocParent = PsiTreeUtil.getParentOfType(this, classOf[ScDocComment])

    if (scalaDocParent == null || !isParamTag)
      return Array.empty[ScNamedElement]

    def filterParamsByName(tagName: String, params: Iterable[ScNamedElement]): Array[ScNamedElement] = {
      val paramsSet =
        (for {
          tag <- scalaDocParent.asInstanceOf[ScDocComment].findTagsByName(tagName)
          if tag.getValueElement != null && tag != getParent
        } yield tag.getValueElement.getText).toSet

      val result = mutable.ArrayBuilder.make[ScNamedElement]
      params.filter(param => !paramsSet.contains(param.name)).foreach(result += _)
      result.result()
    }

    scalaDocParent.getParent match {
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