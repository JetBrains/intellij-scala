package org.jetbrains.plugins.scala.annotator


import com.intellij.psi.search.GlobalSearchScope
import gutter.OverrideGutter
import highlighter.{DefaultHighlighter, AnnotatorHighlighter}
import lang.psi.api.expr._


import lang.psi.api.statements._
import lang.psi.api.toplevel.imports.ScImportSelector
import lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition, ScTrait, ScObject}
import lang.psi.api.toplevel.{ScEarlyDefinitions, ScTyped}
import _root_.scala.collection.mutable.HashSet
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.api.base.patterns.ScBindingPattern
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import lang.lexer.ScalaTokenTypes
import lang.psi.api.statements.params.ScTypeParam
import com.intellij.openapi.util.TextRange
import com.intellij.lang.annotation._

import lang.psi.impl.toplevel.typedef.TypeDefinitionMembers


import lang.psi.types.{FullSignature, PhysicalSignature, ScSubstitutor}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.psi._
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.annotator.intention._
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import quickfix.ImplementMethodsQuickFix
import quickfix.modifiers.{RemoveModifierQuickFix, AddModifierQuickFix}

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

class ScalaAnnotator extends Annotator {

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case x: ScFunction if x.getParent.isInstanceOf[ScTemplateBody] => {
        addOverrideGutter(x, holder)
        checkOverrideMethods(x, holder)
      }
      case x: ScTypeDefinition => {
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

  private def checkImplementedMethods(clazz: ScTypeDefinition, holder: AnnotationHolder) {
    clazz match {
      case _: ScTrait => return
      case _ =>
    }
    if (clazz.getModifierList.getNode.findChildByType(ScalaTokenTypes.kABSTRACT) != null) return
    if (ScalaOIUtil.getMembersToImplement(clazz).length > 0) {
      val error = clazz match {
        case _: ScClass => ScalaBundle.message("class.must.declared.abstract", Array[Object](clazz.getName))
        case _: ScObject => ScalaBundle.message("object.must.implement", Array[Object](clazz.getName))
      }
      val start = clazz.getTextRange.getStartOffset
      var end = clazz.extendsBlock.templateBody match {
        case Some(x) => x.getTextRange.getStartOffset
        case None => clazz.extendsBlock.getTextRange.getEndOffset
      }
      val text = clazz.getContainingFile.getText
      while (end > start && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\n')) end = end - 1
      val annotation: Annotation = holder.createErrorAnnotation(new TextRange(start, end), error)
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)

      annotation.registerFix(new ImplementMethodsQuickFix(clazz))
    }
  }

  private def checkOverrideMethods(method: ScFunction, holder: AnnotationHolder) {
    method.superMethod match {
      case None =>
        if (method.hasModifierProperty("override")) {
          val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
            ScalaBundle.message("method.overrides.nothing", Array[Object](method.getName)))
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          annotation.registerFix(new RemoveModifierQuickFix(method, "override"))
        }
      case Some(superMethod) => if (!method.hasModifierProperty("override")) {
        val isConcrete = superMethod match {
          case _ : ScFunctionDefinition => true
          case _ : ScFunctionDeclaration => false
          case method: PsiMethod if !method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.isConstructor => true
          case _ => false
        }
        if (isConcrete) {
          val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
            ScalaBundle.message("method.needs.override.modifier", Array[Object](method.getName)))
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          annotation.registerFix(new AddModifierQuickFix(method, "override"))
        }
      }
    }
  }

  private def addOverrideGutter(method: ScFunction, holder: AnnotationHolder) {
    val annotation: Annotation = holder.createInfoAnnotation(method, null)

    val supers = method.superMethods
    if (supers.length > 0) annotation.setGutterIconRenderer(
      new OverrideGutter(supers, !method.hasModifierProperty("override")))
  }

  private def checkResultExpression(block: ScBlock, holder: AnnotationHolder) {
    val stat = block.lastStatement match {case None => return case Some(x) => x}
    stat match {
      case _: ScExpression =>
      case _ => {
        val error = ScalaBundle.message("block.must.end.result.expression", Array[Object]())
        val annotation: Annotation = holder.createErrorAnnotation(stat.getTextRange, error)
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}