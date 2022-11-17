package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.IdentityEvaluator
import com.intellij.debugger.engine.{DebugProcessImpl, JVMNameUtil, JavaStackFrame, JavaValue}
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.ui.impl.watch.{NodeManagerImpl, StackFrameDescriptorImpl}
import com.intellij.xdebugger.frame.XValueChildrenList
import com.sun.jdi.{BooleanValue, ObjectReference}
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaMethodEvaluator
import org.jetbrains.plugins.scala.debugger.ui.{LocalLazyValDescriptor, NotInitializedLocalLazyValDescriptor}

import scala.jdk.CollectionConverters._

private final class ScalaStackFrame(descriptor: StackFrameDescriptorImpl, update: Boolean)
  extends JavaStackFrame(descriptor, update) {

  import org.jetbrains.plugins.scala.debugger.ScalaStackFrame.lazyRefClasses

  private val debugProcess: DebugProcessImpl = descriptor.getDebugProcess.asInstanceOf[DebugProcessImpl]

  private val nodeManager: NodeManagerImpl = debugProcess.getXdebugProcess.getNodeManager

  override def buildLocalVariables(
    evaluationContext: EvaluationContextImpl,
    children: XValueChildrenList,
    localVariables: java.util.List[LocalVariableProxyImpl]): Unit = {

    localVariables.forEach { variable =>
      val variableName = variable.name()

      val descriptor =
        if (variableName.endsWith("$lzy") && lazyRefClasses(variable.typeName())) {
          val value = variable.getFrame.getValue(variable)
          val initialized =
            ScalaMethodEvaluator(new IdentityEvaluator(value), "initialized", JVMNameUtil.getJVMRawText("()Z"), Seq.empty, methodOnRuntimeRef = true)
              .evaluate(evaluationContext).asInstanceOf[BooleanValue].value()

          val initName = s"${variableName.dropRight(4)}$$1"
          val allMethods = evaluationContext.computeThisObject().asInstanceOf[ObjectReference].referenceType().allMethods().asScala
          val initMethod = allMethods.find(_.name() == initName).get

          val variableType = variable.getType
          if (initialized) new LocalLazyValDescriptor(evaluationContext.getProject, variable, variableType, initMethod)
          else new NotInitializedLocalLazyValDescriptor(evaluationContext.getProject, variable, variableType, initMethod)
        } else nodeManager.getLocalVariableDescriptor(null, variable)

      children.add(JavaValue.create(null, descriptor, evaluationContext, nodeManager, false))
    }
  }
}

private object ScalaStackFrame {
  private val lazyRefClasses: Set[String] = Set(
    "Ref",
    "Boolean",
    "Byte",
    "Char",
    "Short",
    "Int",
    "Long",
    "Float",
    "Double",
    "Unit"
  ).map("scala.runtime.Lazy" + _)
}
