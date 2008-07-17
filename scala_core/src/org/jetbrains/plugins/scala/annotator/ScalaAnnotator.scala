package org.jetbrains.plugins.scala.annotator

import lang.psi.api.toplevel.{ScEarlyDefinitions, ScTyped}
import lang.psi.api.expr.{ScAnnotationExpr, ScAnnotation, ScNameValuePair, ScReferenceExpression}
import _root_.scala.collection.mutable.HashSet
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.api.toplevel.typedef.ScTrait
import lang.psi.api.base.patterns.ScBindingPattern
import lang.psi.api.base.patterns.ScReferencePattern
import lang.psi.api.statements.ScVariable
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.statements.ScValue
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import lang.psi.api.toplevel.typedef.ScObject
import lang.psi.api.toplevel.typedef.ScClass
import lang.lexer.ScalaTokenTypes
import highlighter.DefaultHighlighter
import lang.psi.api.statements.params.ScTypeParam
import com.intellij.openapi.util.TextRange
import com.intellij.lang.annotation._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.psi._
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.annotator.intention._

/** 
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

class ScalaAnnotator extends Annotator {

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case _: ScSimpleTypeElement if element.getParent.isInstanceOf[ScConstructor] &&
              element.getParent.getParent.isInstanceOf[ScAnnotationExpr] => highlightElement(element, holder) //highlight annotation
      case x: ScReferenceExpression if x.qualifier == None => { //todo: temporary case
        x.bind match {
          case Some(_) => highlightReferenceElement(x, holder)
          case None =>
        }
      }
      case x: ScReferenceElement if x.qualifier == None => checkNotQualifiedReferenceElement(x, holder)
      case x: ScReferenceElement => checkQualifiedReferenceElement(x, holder)
      case _ => highlightElement(element, holder)
    }
  }


  private def checkNotQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    refElement.bind() match {
      case None =>
        //todo: register used imports
        val error = ScalaBundle.message("cannot.resolve", Array[Object](refElement.refName))
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        registerAddImportFix(refElement, annotation)
      case _ => highlightReferenceElement(refElement, holder)
    }
  }

  private def checkQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    refElement.bind() match {
      case None =>
      case _ => highlightReferenceElement(refElement, holder)
    }
  }

  private def registerAddImportFix(refElement: ScReferenceElement, annotation: Annotation) {
    val actions = OuterImportsActionCreator.getOuterImportFixes(refElement, refElement.getProject())
    for (action <- actions) {
      annotation.registerFix(action)
    }
  }

  private def registerUsedImports(refElement: ScStableCodeReferenceElement, annotation: Annotation) {

  }

  private def highlightReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    refElement.resolve match {
      case x: ScClass if x.getModifierList.hasModifierProperty("abstract") => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
      }
      case x: PsiClass if x.getModifierList.hasModifierProperty("abstract") && !x.isInstanceOf[ScClass] => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
      }
      case _: ScTypeParam => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TYPEPARAM)
      }
      case _: ScClass => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.CLASS)
      }
      case _: ScObject => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.OBJECT)
      }
      case _: ScTrait => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TRAIT)
      }
      case _: ScSyntheticClass => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.PREDEF)
      }
      case x: PsiClass if x.isInterface => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TRAIT)
      }
      case _: PsiClass if refElement.isInstanceOf[ScStableCodeReferenceElement] => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.CLASS)
      }
      case _: PsiClass if refElement.isInstanceOf[ScReferenceExpression] => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.OBJECT)
      }
      case x: ScBindingPattern => {
        var parent: PsiElement = x
        while (parent != null && !(parent.isInstanceOf[ScValue] || parent.isInstanceOf[ScVariable])) parent = parent.getParent
        parent match {
          case x: ScValue if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScClass] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
          }
          case x: ScVariable if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScClass] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
          }
          case x: ScValue if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScTrait] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
          }
          case x: ScVariable if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScTrait] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
          }
          case x: ScValue if x.getParent.isInstanceOf[ScEarlyDefinitions] && x.getParent.getParent.getParent.isInstanceOf[ScClass] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
          }
          case x: ScVariable if x.getParent.isInstanceOf[ScEarlyDefinitions] && x.getParent.getParent.getParent.isInstanceOf[ScClass] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
          }
          case x: ScValue if x.getParent.isInstanceOf[ScEarlyDefinitions] && x.getParent.getParent.getParent.isInstanceOf[ScTrait] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
          }
          case x: ScVariable if x.getParent.isInstanceOf[ScEarlyDefinitions] && x.getParent.getParent.getParent.isInstanceOf[ScTrait] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
          }
          case x: ScValue if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScObject] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.OBJECT_FIELD)
          }
          case x: ScVariable if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScObject] => {
            val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
            annotation.setTextAttributes(DefaultHighlighter.OBJECT_FIELD)
          }
          case _ =>
        }
      }
      case x: PsiField if x.getModifierList.hasModifierProperty("static") => {
        val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
        annotation.setTextAttributes(DefaultHighlighter.OBJECT_FIELD)
      }
      case x: PsiField => {
        val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
        annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
      }
      case x => //println("" + x + " " + x.getText)
    }
  }

  private def highlightElement(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case _: ScSimpleTypeElement if element.getParent.isInstanceOf[ScConstructor] &&
              element.getParent.getParent.isInstanceOf[ScAnnotationExpr] => {
        val annotation = holder.createInfoAnnotation(element, null)
        annotation.setTextAttributes(DefaultHighlighter.ANNOTATION)
      }
      case _ if element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER => {
        element.getParent match {
          case _: ScNameValuePair => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.ANNOTATION_ATTRIBUTE)
          }
          case _: ScTypeParam => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.TYPEPARAM)
          }
          case x: ScClass if x.getModifierList.hasModifierProperty("abstract") => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
          }
          case _: ScClass => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.CLASS)
          }
          case _: ScObject => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.OBJECT)
          }
          case _: ScTrait => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.TRAIT)
          }
          case x: ScBindingPattern => {
            var parent: PsiElement = x
            while (parent != null && !(parent.isInstanceOf[ScValue] || parent.isInstanceOf[ScVariable])) parent = parent.getParent
            parent match {
              case x: ScValue if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScClass] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
              }
              case x: ScVariable if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScClass] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
              }
              case x: ScValue if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScTrait] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
              }
              case x: ScVariable if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScTrait] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
              }
              case x: ScValue if x.getParent.isInstanceOf[ScEarlyDefinitions] && x.getParent.getParent.getParent.isInstanceOf[ScClass] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
              }
              case x: ScVariable if x.getParent.isInstanceOf[ScEarlyDefinitions] && x.getParent.getParent.getParent.isInstanceOf[ScClass] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
              }
              case x: ScValue if x.getParent.isInstanceOf[ScEarlyDefinitions] && x.getParent.getParent.getParent.isInstanceOf[ScTrait] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
              }
              case x: ScVariable if x.getParent.isInstanceOf[ScEarlyDefinitions] && x.getParent.getParent.getParent.isInstanceOf[ScTrait] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
              }
              case x: ScValue if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScObject] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.OBJECT_FIELD)
              }
              case x: ScVariable if x.getParent.isInstanceOf[ScTemplateBody] && x.getParent.getParent.getParent.isInstanceOf[ScObject] => {
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.OBJECT_FIELD)
              }
              case _ =>
            }
          }
          case _ =>
        }
      }
      case _ if element.getNode.getElementType == ScalaTokenTypes.tAT && element.getParent.isInstanceOf[ScAnnotation] => {
        val annotation = holder.createInfoAnnotation(element, null)
        annotation.setTextAttributes(DefaultHighlighter.ANNOTATION)
      }
      case _ =>
    }
  }
}