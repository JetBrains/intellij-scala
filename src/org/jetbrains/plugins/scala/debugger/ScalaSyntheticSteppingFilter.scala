package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.NoDataException
import com.intellij.debugger.engine.{ExtraSteppingFilter, SuspendContext}
import com.intellij.psi.PsiElement
import com.sun.jdi.Location
import com.sun.jdi.request.StepRequest
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * @author Nikolay.Tropin
 */
class ScalaSyntheticSteppingFilter extends ExtraSteppingFilter {

  override def isApplicable(context: SuspendContext): Boolean = {
    val debugProcess = context.getDebugProcess
    val positionManager = new ScalaPositionManager(debugProcess)
    val location = context.getFrameProxy.location()
    isSyntheticMethod(location, positionManager)
  }

  override def getStepRequestDepth(context: SuspendContext): Int = StepRequest.STEP_INTO

  private def isSyntheticMethod(location: Location, positionManager: ScalaPositionManager): Boolean = {
    val name = location.method().name()
    if (name.endsWith("$lzycompute")) return false //should step into the body of a lazy val

    if (positionManager.shouldSkip(location)) return true

    if (name.startsWith("apply")) return false

    if (name == "<init>") return false

    inReadAction {
      val sourcePosition =
        try {
          positionManager.getSourcePosition(location)
        } catch {
          case _: NoDataException => return false
        }

      if (!sourcePosition.getFile.getLanguage.is(ScalaLanguage.Instance)) return false

      val classInSource = ScalaPositionManager.findGeneratingClassOrMethodParent(sourcePosition.getElementAt)
      if (classInSource == null) return false

      !existsInSource(name, classInSource)
    }
  }

  private def existsInSource(name: String, td: PsiElement): Boolean = {
    td.depthFirst(elem => elem == td || !ScalaEvaluatorBuilderUtil.isGenerateClass(elem)).exists {
      case fun: ScFunction if fun.isLocal => name.contains(ScalaNamesUtil.toJavaName(fun.name))
      case fun: ScFunction => ScalaNamesUtil.toJavaName(fun.name) == name
      case _ => false
    }
  }
}
