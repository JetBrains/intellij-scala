package org.jetbrains.plugins.scala.highlighter

import _root_.org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner}
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.statements._
import com.intellij.psi._
import lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScBindingPattern}
import lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import lang.psi.api.expr.{ScAnnotationExpr, ScAnnotation, ScReferenceExpression, ScNameValuePair}
import lang.psi.api.base.{ScConstructor, ScReferenceElement, ScStableCodeReferenceElement}
import lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition, ScTrait, ScObject}
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.lexer.ScalaTokenTypes

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.07.2008
 */

object AnnotatorHighlighter {
  def highlightReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    val c = PsiTreeUtil.getParentOfType(refElement, classOf[ScConstructor])
    c match {
      case null =>
      case c => if (c.getParent.isInstanceOf[ScAnnotationExpr]) return
    }
    refElement.resolve match {
      case _: ScSyntheticClass => { //this is td, it's important!
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.PREDEF)
      }
      case x: ScClass if x.getModifierList.has(ScalaTokenTypes.kABSTRACT) => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
      }
      case _: ScTypeParam => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TYPEPARAM)
      }
      case _: ScTypeAlias => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TYPE_ALIAS)
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
      case x: PsiClass if x.isInterface => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TRAIT)
      }
      case x: PsiClass if x.getModifierList != null && x.getModifierList.hasModifierProperty("abstract") => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
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
        while (parent != null && !(parent.isInstanceOf[ScValue] || parent.isInstanceOf[ScVariable]
                || parent.isInstanceOf[ScCaseClause])) parent = parent.getParent
        parent match {
          case r@(_: ScValue | _: ScVariable) => {
            parent.getParent match {
              case _: ScTemplateBody | _: ScEarlyDefinitions => {
                val annotation = holder.createInfoAnnotation(refElement, null)
                r match {
                  case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                    annotation.setTextAttributes(DefaultHighlighter.LAZY)
                  case _: ScValue => annotation.setTextAttributes(DefaultHighlighter.VALUES)
                  case _: ScVariable => annotation.setTextAttributes(DefaultHighlighter.VARIABLES)
                  case _ =>
                }
              }
              case _ => {
                val annotation = holder.createInfoAnnotation(refElement, null)
                r match {
                  case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                    annotation.setTextAttributes(DefaultHighlighter.LOCAL_LAZY)
                  case _: ScValue => annotation.setTextAttributes(DefaultHighlighter.LOCAL_VALUES)
                  case _: ScVariable => annotation.setTextAttributes(DefaultHighlighter.LOCAL_VARIABLES)
                  case _ =>
                }
              }
            }
          }
          case _: ScCaseClause => {
            val annotation = holder.createInfoAnnotation(refElement, null)
            annotation.setTextAttributes(DefaultHighlighter.PATTERN)
          }
          case _ =>
        }
      }
      case x: PsiField => {
        val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
        if (!x.hasModifierProperty("final")) annotation.setTextAttributes(DefaultHighlighter.VARIABLES)
        else annotation.setTextAttributes(DefaultHighlighter.VALUES)
      }
      case x: ScParameter => {
        val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
        annotation.setTextAttributes(DefaultHighlighter.PARAMETER)
      }
      case _: ScFunctionDefinition | _: ScFunctionDeclaration => {
        val x = refElement.resolve
        if (x != null) {
          x.getParent match {
            case _: ScTemplateBody | _: ScEarlyDefinitions => {
              x.getParent.getParent.getParent match {
                case _: ScClass | _: ScTrait => {
                  val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
                  annotation.setTextAttributes(DefaultHighlighter.METHOD_CALL)
                }
                case _: ScObject => {
                  val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
                  annotation.setTextAttributes(DefaultHighlighter.OBJECT_METHOD_CALL)
                }
                case _ =>
              }
            }
            case _ => {
              val annotation = holder.createInfoAnnotation(refElement, null)
              annotation.setTextAttributes(DefaultHighlighter.LOCAL_METHOD_CALL)
            }
          }
        }
      }
      case x: PsiMethod => {
        if (x.getModifierList.hasModifierProperty("static")) {
          val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
          annotation.setTextAttributes(DefaultHighlighter.OBJECT_METHOD_CALL)
        } else {
          val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
          annotation.setTextAttributes(DefaultHighlighter.METHOD_CALL)
        }
      }
      case x => //println("" + x + " " + x.getText)
    }
  }

  def highlightElement(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case x: ScAnnotation => visitAnnotation(x, holder)
      case x: ScParameter => visitParameter(x, holder)
      case x: ScCaseClause => visitCaseClause(x, holder)
      case x: ScTypeAlias => visitTypeAlias(x, holder)
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
          case clazz: ScClass => {
            if (clazz.getModifierList.has(ScalaTokenTypes.kABSTRACT)) {
              val annotation = holder.createInfoAnnotation(clazz.nameId, null)
              annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
            } else {
              val annotation = holder.createInfoAnnotation(clazz.nameId, null)
              annotation.setTextAttributes(DefaultHighlighter.CLASS)
            }
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
              case r@(_: ScValue | _: ScVariable) => {
                parent.getParent match {
                  case _: ScTemplateBody | _: ScEarlyDefinitions => {
                    val annotation = holder.createInfoAnnotation(element, null)
                    r match {
                      case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                        annotation.setTextAttributes(DefaultHighlighter.LAZY)
                      case _: ScValue => annotation.setTextAttributes(DefaultHighlighter.VALUES)
                      case _: ScVariable => annotation.setTextAttributes(DefaultHighlighter.VARIABLES)
                      case _ =>
                    }
                  }
                  case _ => {
                    val annotation = holder.createInfoAnnotation(element, null)
                    r match {
                      case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                        annotation.setTextAttributes(DefaultHighlighter.LOCAL_LAZY)
                      case _: ScValue => annotation.setTextAttributes(DefaultHighlighter.LOCAL_VALUES)
                      case _: ScVariable => annotation.setTextAttributes(DefaultHighlighter.LOCAL_VARIABLES)
                      case _ =>
                    }
                  }
                }
              }
              case _ =>
            }
          }
          case _: ScFunctionDefinition | _: ScFunctionDeclaration => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.METHOD_DECLARATION)
          }
          case _ =>
        }
      }
      case _ =>
    }
  }

  private def visitAnnotation(annotation: ScAnnotation, holder: AnnotationHolder): Unit = {
    val annotation1 = holder.createInfoAnnotation(annotation.getFirstChild, null)
    annotation1.setTextAttributes(DefaultHighlighter.ANNOTATION)
    val element = annotation.annotationExpr.constr.typeElement
    val annotation2 = holder.createInfoAnnotation(element, null)
    annotation2.setTextAttributes(DefaultHighlighter.ANNOTATION)
  }

  private def visitTypeAlias(typeAlias: ScTypeAlias, holder: AnnotationHolder): Unit = {
    val annotation = holder.createInfoAnnotation(typeAlias.nameId, null)
    annotation.setTextAttributes(DefaultHighlighter.TYPE_ALIAS)
  }

  private def visitClass(clazz: ScClass, holder: AnnotationHolder): Unit = {
    if (clazz.getModifierList.has(ScalaTokenTypes.kABSTRACT)) {
      val annotation = holder.createInfoAnnotation(clazz.nameId, null)
      annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
    } else {
      val annotation = holder.createInfoAnnotation(clazz.nameId, null)
      annotation.setTextAttributes(DefaultHighlighter.CLASS)
    }
  }

  private def visitParameter(param: ScParameter, holder: AnnotationHolder): Unit = {
    val annotation = holder.createInfoAnnotation(param.nameId, null)
    annotation.setTextAttributes(DefaultHighlighter.PARAMETER)
  }

  private def visitPattern(pattern: ScPattern, holder: AnnotationHolder): Unit = {
    for (binding <- pattern.bindings if !binding.isWildcard) {
      val annotation = holder.createInfoAnnotation(binding, null)
      annotation.setTextAttributes(DefaultHighlighter.PATTERN)
    }
  }

  private def visitCaseClause(clause: ScCaseClause, holder: AnnotationHolder): Unit = {
    clause.pattern match {
      case Some(x) => visitPattern(x, holder)
      case None =>
    }
  }
}