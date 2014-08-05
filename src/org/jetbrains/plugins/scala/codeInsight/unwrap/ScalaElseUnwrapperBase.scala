package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIfStmt}

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
abstract class ScalaElseUnwrapperBase extends ScalaUnwrapper {

  override def isApplicableTo(e: PsiElement) = elseBranch(e).isDefined

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = elseBranch(element) match {
    case Some((ifStmt, expr)) => unwrapElseBranch(expr, ifStmt, context)
    case _ =>
  }

  protected def elseBranch(e: PsiElement): Option[(ScIfStmt, ScExpression)] = {
    if (e.isInstanceOf[ScIfStmt]) return None

    e.getParent match {
      case ifSt @ ScIfStmt(_, Some(expr), _) childOf (parentIf @ ScIfStmt(_, _, Some(elseIf))) if ifSt == elseIf && e == expr =>
        Some((parentIf, expr))
      case ifStmt @ ScIfStmt(_, _, Some(elseBr)) =>
        if (e.getNode.getElementType == ScalaTokenTypes.kELSE || elseBr == e)
          Some((ifStmt, elseBr))
        else None
      case _ => None
    }
  }

  @tailrec
  final def maxIfStmt(ifStmt: ScIfStmt): ScIfStmt = ifStmt.getParent match {
    case ifSt: ScIfStmt => maxIfStmt(ifSt)
    case _ => ifStmt
  }

  protected def unwrapElseBranch(expr: ScExpression, ifStmt: ScIfStmt, context: ScalaUnwrapContext)
}
