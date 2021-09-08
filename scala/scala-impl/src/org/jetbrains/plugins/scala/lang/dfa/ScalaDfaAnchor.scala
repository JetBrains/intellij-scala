package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement

sealed trait ScalaDfaAnchor extends DfaAnchor
case class ScalaStatementAnchor(statement: ScBlockStatement) extends ScalaDfaAnchor
case class ScalaUnreportedElementAnchor(element: PsiElement) extends ScalaDfaAnchor
