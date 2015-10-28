package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.{DebugProcess, ExtraSteppingFilter, SuspendContext}
import com.intellij.psi.PsiElement
import com.sun.jdi.Location
import com.sun.jdi.request.StepRequest
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * @author Nikolay.Tropin
 */
class ScalaSyntheticSteppingFilter extends ExtraSteppingFilter {

  override def isApplicable(context: SuspendContext): Boolean = {
    val debugProcess = context.getDebugProcess
    val frameProxy = context.getFrameProxy
    if (debugProcess == null || frameProxy == null) return false

    val location = frameProxy.location()
    isSynthetic(location, debugProcess)
  }

  override def getStepRequestDepth(context: SuspendContext): Int = StepRequest.STEP_INTO

  def isSynthetic(location: Location, debugProcess: DebugProcess): Boolean = {
    val positionManager = new ScalaPositionManager(debugProcess)

    val method = location.method()
    val name = method.name()
    if (name.endsWith("$lzycompute")) return false //should step into the body of a lazy val

    if (positionManager.shouldSkip(location)) return true

    if (name.startsWith("apply") || ScalaPositionManager.isIndyLambda(method)) return false

    if (method.isConstructor) return false

    inReadAction {
      positionManager.findElementByReferenceType(location.declaringType()) match {
        case Some(td: ScTemplateDefinition) =>
          td.functions.forall(f => !nameMatches(name, f.name)) && !hasLocalFun(name, td)
        case _ => false
      }
    }
  }

  private def hasLocalFun(name: String, td: PsiElement): Boolean = {
    td.depthFirst(elem => elem == td || !ScalaEvaluatorBuilderUtil.isGenerateClass(elem)).exists {
      case fun: ScFunction if fun.isLocal => nameMatches(name, fun.name)
      case _ => false
    }
  }

  private def nameMatches(jvmName: String, funName: String) = {
    val encoded = ScalaNamesUtil.toJavaName(funName)
    encoded == jvmName || jvmName.startsWith(encoded + "$") || jvmName.contains("$$" + encoded + "$")
  }
}
