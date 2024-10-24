package org.jetbrains.plugins.scala.debugger.smartStepInto

import com.intellij.debugger.engine._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Range
import com.sun.jdi.Location
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants.TraitImplementationClassSuffix_211
import org.jetbrains.plugins.scala.util.TopLevelMembers.{findEnclosingPackageOrFile, topLevelMemberClassName}

class ScalaMethodFilter(function: ScMethodLike, callingExpressionLines: Range[Integer]) extends MethodFilter {
  private val unknownName: String = "!unknownName!"
  private val myTargetMethodSignature = DebuggerUtil.getFunctionJVMSignature(function)
  private val myDeclaringClassName = {
    val clazz = PsiTreeUtil.getParentOfType(function, classOf[ScTemplateDefinition])
    if (clazz == null) {
      val isScala3 = function.scalaMinorVersion.exists(_.isScala3)
      val name =
        if (isScala3) {
          findEnclosingPackageOrFile(function).map {
            case Left(pkg) => topLevelMemberClassName(pkg.getContainingFile, Some(pkg))
            case Right(file) => topLevelMemberClassName(file, None)
          }.getOrElse(unknownName)
        } else
          unknownName
      JVMNameUtil.getJVMRawText(name)
    } else DebuggerUtil.getClassJVMName(clazz, clazz.isInstanceOf[ScObject] || ValueClassType.isValueClass(clazz))
  }
  private val funName = function match {
    case ScalaConstructor(_) => "<init>"
    case fun: ScFunction => ScalaNamesUtil.toJavaName(fun.name)
    case _ => unknownName
  }

  override def locationMatches(process: DebugProcessImpl, location: Location): Boolean = {
    val method = location.method()
    if (!method.name.contains(funName)) return false

    val className = myDeclaringClassName.getName(process)
    if (className == unknownName) return false

    val locationTypeName = location.declaringType().name()

    if (locationTypeName.endsWith(TraitImplementationClassSuffix_211))
      className == locationTypeName.stripSuffix(TraitImplementationClassSuffix_211)
    else if (myTargetMethodSignature != null && method.signature() != myTargetMethodSignature.getName(process))
      false
    else
      DebuggerUtils.instanceOf(location.declaringType(), locationTypeName) &&
        !ScalaPositionManager.shouldSkip(location, process)
  }

  override def getCallingExpressionLines: Range[Integer] = callingExpressionLines
}
