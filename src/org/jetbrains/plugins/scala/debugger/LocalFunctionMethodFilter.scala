package org.jetbrains.plugins.scala
package debugger

import com.intellij.debugger.engine._
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Range
import com.sun.jdi.Location
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * Nikolay.Tropin
 * 2014-06-10
 */

class LocalFunctionMethodFilter(function: ScFunction, callingExpressionLines: Range[Integer]) extends MethodFilter {

  val myTargetMethodSignature = DebuggerUtil.getFunctionJVMSignature(function)
  val myDeclaringClassName = {
    val clazz = PsiTreeUtil.getParentOfType(function, classOf[ScTemplateDefinition])
    JVMNameUtil.getJVMQualifiedName(clazz)
  }

  override def locationMatches(process: DebugProcessImpl, location: Location): Boolean = {
    val method = location.method()
    if (!method.name.startsWith(function.name)) false
    else if (myTargetMethodSignature != null && method.signature() != myTargetMethodSignature.getName(process)) false
    else DebuggerUtilsEx.isAssignableFrom(myDeclaringClassName.getName(process), location.declaringType)
  }

  override def getCallingExpressionLines = callingExpressionLines
}
