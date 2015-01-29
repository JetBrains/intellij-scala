package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.NoDataException
import com.intellij.debugger.engine.{ExtraSteppingFilter, SuspendContext}
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.Location
import com.sun.jdi.request.StepRequest
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * @author Nikolay.Tropin
 */
class ScalaSyntheticSteppingFilter extends ExtraSteppingFilter {
  
  override def isApplicable(context: SuspendContext): Boolean = {
    val debugProcess = context.getDebugProcess
    val positionManager = new ScalaPositionManager(debugProcess)
    val location = context.getFrameProxy.location()
    inReadAction {
      isSyntheticMethod(location, positionManager)
    }
  }

  override def getStepRequestDepth(context: SuspendContext): Int = StepRequest.STEP_INTO

  private def isSyntheticMethod(location: Location, positionManager: ScalaPositionManager): Boolean = {
    val sourcePosition =
      try {
        positionManager.getSourcePosition(location)
      } catch {
        case _: NoDataException => return false
      }

    if (!sourcePosition.getFile.getLanguage.is(ScalaLanguage.Instance)) return false

    val classInSource = PsiTreeUtil.getParentOfType(sourcePosition.getElementAt, classOf[ScTemplateDefinition], false)
    if (classInSource == null) return false

    val name = location.method().name()
    if (name.endsWith("$lzycompute")) return false //should step into the body of a lazy val

    val functionsWithName = classInSource.functions.filter(_.name == name)
    functionsWithName.isEmpty
  }
}
