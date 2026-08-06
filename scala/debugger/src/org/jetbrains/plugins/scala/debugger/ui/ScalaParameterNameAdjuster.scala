package org.jetbrains.plugins.scala.debugger.ui

import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl
import com.intellij.debugger.ui.tree.{NodeDescriptor, NodeDescriptorNameAdjuster}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

import scala.util.Try

class ScalaParameterNameAdjuster extends NodeDescriptorNameAdjuster {
  override def isApplicable(descriptor: NodeDescriptor): Boolean = {
    descriptor match {
      case vd: LocalVariableDescriptorImpl if vd.getName == "$this" => false
      case vd: LocalVariableDescriptorImpl =>
        ScalaParameterNameAdjuster.isScalaArgument(vd.getLocalVariable) && vd.getName.contains("$")
      case _ => false
    }
  }

  override def fixName(name: String, descriptor: NodeDescriptor): String = ScalaParameterNameAdjuster.fixName(name)
}

object ScalaParameterNameAdjuster {
  private[debugger] def fixName(name: String): String = name.takeWhile(_ != '$')

  private def isScalaArgument(variable: LocalVariableProxyImpl) = {
    Try {
      variable.getVariable.isArgument && DebuggerUtil.isScala(variable.getFrame.location().declaringType())
    }.getOrElse(false)
  }
}

