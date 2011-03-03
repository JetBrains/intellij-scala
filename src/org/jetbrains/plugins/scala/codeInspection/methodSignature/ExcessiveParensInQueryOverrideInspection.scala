package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.Extensions._

class ExcessiveParensInQueryOverrideInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Excessive parens in Java query method override"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Method adds parens that is not needed for Java query method"

  override def getID = "ExcessiveParensInQueryOverride"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.hasEmptyParens && !f.hasUnitReturnType =>
      f.superMethod match {
        case Some(_: ScalaPsiElement) => // do nothing
        case Some(method) if method.isQuery =>
          holder.registerProblem(f.nameId, getDisplayName, new RemoveParensQuickFix(f))
        case _ =>
      }
  }
}