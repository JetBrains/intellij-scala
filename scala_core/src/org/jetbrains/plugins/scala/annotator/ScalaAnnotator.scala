package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.search.GlobalSearchScope
import highlighter.{AnnotatorHighlighter}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.expr._


import lang.psi.api.statements._
import lang.psi.api.statements.params.{ScClassParameter, ScTypeParam}
import lang.psi.api.toplevel.imports.ScImportSelector
import lang.psi.api.toplevel.typedef._
import lang.psi.api.toplevel.{ScEarlyDefinitions, ScTyped}
import _root_.scala.collection.mutable.HashSet
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.api.base.patterns.ScBindingPattern
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.openapi.util.TextRange
import com.intellij.lang.annotation._

import lang.psi.impl.toplevel.typedef.TypeDefinitionMembers


import lang.psi.ScalaPsiUtil
import lang.psi.types.{FullSignature, PhysicalSignature, Signature, ScSubstitutor}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.psi._
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.annotator.intention._
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import quickfix.ImplementMethodsQuickFix
import quickfix.modifiers.{RemoveModifierQuickFix, AddModifierQuickFix}

/**
 *    User: Alexander Podkhalyuzin
 *    Date: 23.06.2008
 */

class ScalaAnnotator extends Annotator {
  def annotate(element: PsiElement, holder: AnnotationHolder) {
    val file = element.getContainingFile
    val fType = file.getVirtualFile.getFileType
    if (fType != ScalaFileType.SCALA_FILE_TYPE) return
    element match {
      case x: ScFunction if x.getParent.isInstanceOf[ScTemplateBody] => {
        checkOverrideMethods(x, holder)
      }
      case x: ScTemplateDefinition => {
        checkImplementedMethods(x, holder)
      }
      case x: ScBlock => {
        checkResultExpression(x, holder)
      }
      case x: ScReferenceExpression if x.qualifier == None => {
        x.bind match {
          case Some(_) => AnnotatorHighlighter.highlightReferenceElement(x, holder)
          case None =>
        }
      }
      case x: ScReferenceElement if x.qualifier == None => checkNotQualifiedReferenceElement(x, holder)
      case x: ScReferenceElement => checkQualifiedReferenceElement(x, holder)
      case _ => AnnotatorHighlighter.highlightElement(element, holder)
    }
  }

  private def checkNotQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {

    def processError = {
      val error = ScalaBundle.message("cannot.resolve", Array[Object](refElement.refName))
      val annotation = holder.createErrorAnnotation(refElement.nameId, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      if (refElement.getManager.isInProject(refElement) && refElement.multiResolve(false).length == 0) {
        registerAddImportFix(refElement, annotation)
      }
    }

    refElement.bind() match {
      case None =>
        refElement.getParent match {
          case s: ScImportSelector if refElement.multiResolve(false).length > 0 =>
          case _ => processError
        }
      case Some(result) => {
        registerUsedImports(refElement, result)
        AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
      }
    }
  }

  private def checkQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    refElement.bind() match {
      case None =>
      case _ => AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
    }
  }

  private def registerAddImportFix(refElement: ScReferenceElement, annotation: Annotation) {
    val actions = OuterImportsActionCreator.getOuterImportFixes(refElement, refElement.getProject())
    for (action <- actions) {
      annotation.registerFix(action)
    }
  }

  private def registerUsedImports(refElement: ScReferenceElement, result: ScalaResolveResult) {
    //todo: add body
  }

  private def checkImplementedMethods(clazz: ScTemplateDefinition, holder: AnnotationHolder) {
    clazz match {
      case _: ScTrait => return
      case _ =>
    }
    if (clazz.hasModifierProperty("abstract")) return
    if (ScalaOIUtil.getMembersToImplement(clazz).length > 0) {
      val error = clazz match {
        case _: ScClass => ScalaBundle.message("class.must.declared.abstract", Array[Object](clazz.getName))
        case _: ScObject => ScalaBundle.message("object.must.implement", Array[Object](clazz.getName))
        case _: ScNewTemplateDefinition =>ScalaBundle.message("anonymous.class.must.declared.abstract", Array[Object]())
      }
      val start = clazz.getTextRange.getStartOffset
      val eb = clazz.extendsBlock
      var end = eb.templateBody match {
        case Some(x) => {
          val shifted = eb.findElementAt(x.getStartOffsetInParent - 1) match {case w: PsiWhiteSpace => w case _ => x}
          shifted.getTextRange.getStartOffset
        }
        case None => eb.getTextRange.getEndOffset
      }

      val annotation: Annotation = holder.createErrorAnnotation(new TextRange(start, end), error)
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)

      annotation.registerFix(new ImplementMethodsQuickFix(clazz))
    }
  }

  private def checkOverrideMethods(method: ScFunction, holder: AnnotationHolder) {
    val signatures = method.superSignatures
    if (signatures.length == 0) {
      if (method.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
          ScalaBundle.message("method.overrides.nothing", Array[Object](method.getName)))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        annotation.registerFix(new RemoveModifierQuickFix(method, "override"))
      }
    } else {
      if (!method.hasModifierProperty("override") && !method.isInstanceOf[ScFunctionDeclaration]) {
        def isConcrete(signature: FullSignature): Boolean = if (signature.element.isInstanceOf[PsiNamedElement])
          ScalaPsiUtil.nameContext(signature.element.asInstanceOf[PsiNamedElement]) match {
          case _: ScFunctionDefinition => true
          case method: PsiMethod if !method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.isConstructor => true
          case _: ScPatternDefinition => true
          case _: ScVariableDeclaration => true
          case _: ScClassParameter => true
          case _ => false
        } else false
        def isConcretes: Boolean = {
          for (signature <- signatures if isConcrete(signature)) return true
          return false
        }
        if (isConcretes) {
          val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
            ScalaBundle.message("method.needs.override.modifier", Array[Object](method.getName)))
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          annotation.registerFix(new AddModifierQuickFix(method, "override"))
        }
      }
    }
  }

  private def checkResultExpression(block: ScBlock, holder: AnnotationHolder) {
    var last = block.getNode.getLastChildNode
    while (last != null && (last.getElementType == ScalaTokenTypes.tRBRACE ||
            (ScalaTokenTypes.WHITES_SPACES_TOKEN_SET contains last.getElementType) ||
            (ScalaTokenTypes.COMMENTS_TOKEN_SET contains last.getElementType))) last = last.getTreePrev
    if (last.getElementType == ScalaTokenTypes.tSEMICOLON) return

    val stat = block.lastStatement match {case None => return case Some(x) => x}
    stat match {
      case _: ScExpression =>
      case _ => {
        val error = ScalaBundle.message("block.must.end.result.expression", Array[Object]())
        val annotation: Annotation = holder.createErrorAnnotation(
          new TextRange(stat.getTextRange.getStartOffset, stat.getTextRange.getStartOffset + 3),
          error)
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}