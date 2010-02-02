package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.expr.{ScSuperReference, ScThisReference}
import api.base.{ScReferenceElement, ScPathElement}
import api.toplevel.ScTypedDefinition
import com.intellij.psi.PsiElement
import result.{TypeResult, TypingContext}

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

  override def equiv(t: ScType) = t match {
    case ScSingletonType(path1) => {
      def equiv(e1: ScPathElement, e2: ScPathElement): Boolean = {
        (e1, e2) match {
          case (r1: ScReferenceElement, r2: ScReferenceElement) =>
            r1.bind == r2.bind && ((r1.qualifier, r2.qualifier) match {
              case (Some(q1: ScPathElement), Some(q2: ScPathElement)) => equiv(q1, q2)
              case (None, None) => true
              case _ => false
            })
          case (t1: ScThisReference, t2: ScThisReference) => t1.refTemplate == t2.refTemplate
          case (s1: ScSuperReference, s2: ScSuperReference) => s1.drvTemplate == s2.drvTemplate &&
                  ((s1.staticSuper, s2.staticSuper) match {
                    case (Some(t1), Some(t2)) => t1 equiv t2
                    case (None, None) => true
                    case _ => false
                  }) &&
                  //we can come to the same classes from different outer classes' "super"
                  ((s1.qualifier, s2.qualifier) match {
                    case (Some(q1), Some(q2)) => equiv(q1, q2)
                    case (None, None) => true
                    case _ => false
                  })
          case _ => false
        }
      }
      equiv(path, path1)
    }
    case des: ScDesignatorType => des equiv this
    case proj: ScProjectionType => proj equiv this
    case _ => false
  }
}
