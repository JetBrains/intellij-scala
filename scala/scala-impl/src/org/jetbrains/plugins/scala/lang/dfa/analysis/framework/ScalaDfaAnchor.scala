package org.jetbrains.plugins.scala.lang.dfa.analysis.framework

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement

sealed trait ScalaDfaAnchor extends DfaAnchor
case class ScalaStatementAnchor(statement: ScBlockStatement) extends ScalaDfaAnchor
