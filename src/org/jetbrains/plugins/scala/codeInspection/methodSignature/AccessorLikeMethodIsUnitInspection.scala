package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Pavel Fatin
 */

class AccessorLikeMethodIsUnitInspection extends AbstractMethodSignatureInspection(
  "ScalaAccessorLikeMethodIsUnit", "Method with accessor-like name has Unit result type") {

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.isValid && f.hasQueryLikeName && f.hasUnitResultType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName)
  }
}