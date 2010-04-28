package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.expr.{ScSuperReference, ScThisReference}
import api.base.{ScReferenceElement, ScPathElement}
import api.toplevel.ScTypedDefinition
import result.{TypeResult, TypingContext}
import resolve.ScalaResolveResult
import com.intellij.psi.{PsiPackage, PsiElement}

/**
 * @author ilyas
 */

case class ScSingletonType(path: ScPathElement) extends ValueType {

  // todo rewrite me!
  lazy /* to prevent SOE */
  val pathType = pathTypeInContext(None)

  type HasType = {
    def getType(ctx: TypingContext): TypeResult[ScType]
  }

  def pathTypeInContext(qualifier: Option[PsiElement]): ScType = path match {
    case ref: ScReferenceElement => ref.bind match {
      case None => Nothing
      case Some(r) => r.element match {
        case typed: ScTypedDefinition => typed.getType(TypingContext.empty).getOrElse(Nothing)
        case e => new ScDesignatorType(e)
      }
    }
    case thisPath: ScThisReference => qualifier match {
      case Some(tc: HasType) => tc.getType(TypingContext.empty).getOrElse(Nothing)
      case _ => {
        thisPath.refTemplate match {
          case Some(tmpl) => tmpl.getType(TypingContext.empty).getOrElse(Nothing)
          case _ => Nothing
        }
      }
    }
    case superPath: ScSuperReference => superPath.staticSuper match {
      case Some(t) => t
      case _ => superPath.drvTemplate match {
        case Some(clazz) => new ScDesignatorType(clazz)
        case _ => Nothing
      }
    }
  }
}
