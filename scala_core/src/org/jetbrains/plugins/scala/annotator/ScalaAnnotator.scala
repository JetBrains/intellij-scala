package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.search.GlobalSearchScope
import gutter.OverrideGutter
import highlighter.{DefaultHighlighter, AnnotatorHighlighter}
import lang.psi.api.expr._
import lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
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
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.psi._
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.annotator.intention._
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import quickfix.ImplementMethodsQuickFix

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

class ScalaAnnotator extends Annotator {

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case x: ScFunction => {
        //addOverrideGutter(x, holder)
      }
      case x: ScTypeDefinition => {
        checkImplementedMethods(x, holder)
      }
      case x: ScBlock => {
        checkResultExpression(x, holder)
      }
      case x: ScReferenceExpression if x.qualifier == None => { //todo: temporary case
        x.bind match {
          case Some(_) => AnnotatorHighlighter.highlightReferenceElement(x, holder)
          case None => {
            val myProject = x.getProject
            val function = JavaPsiFacade.getInstance(myProject).getShortNamesCache().getClassesByName _
            val classes = function(x.refName, GlobalSearchScope.allScope(myProject)).filter((y: PsiClass) =>
                y match {
                  case _: ScObject => true
                  case _ => false
                })
            if (classes.length > 0) checkNotQualifiedReferenceElement(x, holder)
          }
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

  private def addOverrideGutter(method: ScFunction, holder: AnnotationHolder) {
    val annotation: Annotation = holder.createInfoAnnotation(method, null)

    val supers = method.findSuperMethods
    if (supers.length > 0) 
      annotation.setGutterIconRenderer(new OverrideGutter(supers, method.getModifierList.getNode.findChildByType(ScalaTokenTypes.kOVERRIDE) == null))
  }

  private def checkResultExpression(block: ScBlock, holder: AnnotationHolder) {
    def testChild(child: PsiElement): Boolean = child match {
      case null => false
      case _: PsiWhiteSpace => true
      case _: PsiComment => true
      case _ => {
        child.getNode.getElementType match {
          case ScalaTokenTypes.tRBRACE => true
          case ScalaTokenTypes.tLBRACE => true
          case ScalaTokenTypes.tLINE_TERMINATOR => true
          case _ => false
        }
      }
    }
    var child = block.getLastChild
    while (testChild(child)) child = child.getPrevSibling
    child match {
      case null =>
      case _: ScExpression =>
      case _ if child.getNode.getElementType == ScalaTokenTypes.tSEMICOLON =>
      case _ => {
        val error = ScalaBundle.message("block.must.end.result.expression", Array[Object]())
        val annotation: Annotation = holder.createErrorAnnotation(child.getTextRange, error)
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}