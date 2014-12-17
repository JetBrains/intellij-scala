package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.{ExtraSteppingFilter, SuspendContext}
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.Location
import com.sun.jdi.request.StepRequest
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

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
    val classInSource = inReadAction {
      val sourcePosition = positionManager.getSourcePosition(location)
      if (!sourcePosition.getFile.getLanguage.is(ScalaLanguage.Instance)) return false

      PsiTreeUtil.getParentOfType(sourcePosition.getElementAt, classOf[ScTemplateDefinition], false)
    }

    val name = location.method().name()
    val membersInSource = classInSource.members.filter {
      case named: ScNamedElement if named.name == name => true
      case holder: ScDeclaredElementsHolder if  holder.declaredElements.exists(_.name == name) => true
      case _ => false
    }
    membersInSource.isEmpty
  }
}
