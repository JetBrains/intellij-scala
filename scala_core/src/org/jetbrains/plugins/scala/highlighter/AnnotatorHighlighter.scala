package org.jetbrains.plugins.scala.highlighter

import lang.psi.api.expr.{ScAnnotationExpr, ScAnnotation, ScReferenceExpression, ScNameValuePair}
import lang.psi.api.base.{ScConstructor, ScReferenceElement, ScStableCodeReferenceElement}
import com.intellij.psi.{PsiField, PsiElement, PsiClass}
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.api.base.patterns.ScBindingPattern
import lang.psi.api.statements.params.ScTypeParam
import lang.psi.api.toplevel.ScEarlyDefinitions
import lang.psi.api.statements.{ScValue, ScVariable}
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScObject}
import lang.lexer.ScalaTokenTypes
/**
* User: Alexander Podkhalyuzin
* Date: 17.07.2008
*/

object AnnotatorHighlighter {
  def highlightReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    refElement.resolve match {
      case x: ScClass if x.getModifierList.hasModifierProperty("abstract") => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
      }
      case x: PsiClass if x.getModifierList.hasModifierProperty("abstract") => {
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
          case _: ScValue | _: ScVariable => {
            parent.getParent match {
              case _: ScTemplateBody | _: ScEarlyDefinitions => {
                parent.getParent.getParent.getParent match {
                  case _: ScClass | _: ScTrait => {
                    val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
                    annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
                  }
                  case _: ScObject => {
                    val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
                    annotation.setTextAttributes(DefaultHighlighter.OBJECT_FIELD)
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

  def highlightElement(element: PsiElement, holder: AnnotationHolder) {
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
              case _: ScValue | _: ScVariable => {
                parent.getParent match {
                  case _: ScTemplateBody | _: ScEarlyDefinitions => {
                    parent.getParent.getParent.getParent match {
                      case _: ScClass | _: ScTrait => {
                        val annotation = holder.createInfoAnnotation(element, null)
                        annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD)
                      }
                      case _: ScObject => {
                        val annotation = holder.createInfoAnnotation(element, null)
                        annotation.setTextAttributes(DefaultHighlighter.OBJECT_FIELD)
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