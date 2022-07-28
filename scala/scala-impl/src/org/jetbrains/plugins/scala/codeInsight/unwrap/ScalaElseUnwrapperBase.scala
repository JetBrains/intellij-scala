package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIf}

import scala.annotation.tailrec

abstract class ScalaElseUnwrapperBase extends ScalaUnwrapper {

  override def isApplicableTo(e: PsiElement): Boolean = elseBranch(e).isDefined

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = elseBranch(element) match {
    case Some((ifStmt, expr)) => unwrapElseBranch(expr, ifStmt, context)
    case _ =>
  }

  protected def elseBranch(e: PsiElement): Option[(ScIf, ScExpression)] = {
    if (e.isInstanceOf[ScIf]) return None

    e.getParent match {
      case ifSt @ ScIf(_, Some(expr), _) childOf (parentIf @ ScIf(_, _, Some(elseIf))) if ifSt == elseIf && e == expr =>
        Some((parentIf, expr))
      case ifStmt @ ScIf(_, _, Some(elseBr)) =>
        if (e.getNode.getElementType == ScalaTokenTypes.kELSE || elseBr == e)
          Some((ifStmt, elseBr))
        else None
      case _ => None
    }
  }

  @tailrec
  final def maxIfStmt(ifStmt: ScIf): ScIf = ifStmt.getParent match {
    case ifSt: ScIf => maxIfStmt(ifSt)
    case _ => ifStmt
  }

  protected def unwrapElseBranch(expr: ScExpression, ifStmt: ScIf, context: ScalaUnwrapContext): Unit
}
