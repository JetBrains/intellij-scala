package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * @param method          original method for which the analysis is currently being performed
 * @param invocationDepth the depth of current analysis. It should start from 1 and be incremented
 *                        before the start of each external method analysis. Used to limit how deep the
 *                        interprocedural analysis will go, compared against the configurable
 *                        [[InterproceduralAnalysis.InterproceduralAnalysisDepthLimit]],
 */
final case class AnalysedMethodInfo(method: ScFunctionDefinition, invocationDepth: Int)
