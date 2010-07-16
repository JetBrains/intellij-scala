package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base._
import resolve.processor.MethodResolveProcessor
import psi.types.Compatibility.Expression
import resolve.{StdKinds, ScalaResolveResult}
import com.intellij.psi.{ResolveState, PsiClass, PsiMethod}
import psi.types.result.{TypingContext, Success}
import psi.types.{ScType, ScSubstitutor}
import api.base.types.{ScTypeElement, ScSimpleTypeElement}
import api.toplevel.templates.{ScExtendsBlock, ScClassParents}
import api.expr.ScNewTemplateDefinition

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScConstructorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScConstructor {

  override def toString: String = "Constructor"


  def expectedType: Option[ScType] = {
    getContext match {
      case parents: ScClassParents => {
        if (parents.typeElements.length != 1) None
        else {
          parents.getContext match {
            case e: ScExtendsBlock => {
              e.getContext match {
                case n: ScNewTemplateDefinition => {
                  n.expectedType
                }
                case _ => None
              }
            }
            case _ => None
          }
        }
      }
      case _ => None
    }
  }

  def newTemplate = {
    getContext match {
      case parents: ScClassParents => {
        parents.getContext match {
          case e: ScExtendsBlock => {
            e.getContext match {
              case n: ScNewTemplateDefinition => {
                Some(n)
              }
              case _ => None
            }
          }
        }
      }
      case _ => None
    }
  }
}