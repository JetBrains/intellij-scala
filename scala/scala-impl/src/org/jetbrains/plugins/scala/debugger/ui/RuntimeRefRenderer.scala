package org.jetbrains.plugins.scala
package debugger.ui

import java.lang
import java.util.concurrent.CompletableFuture

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl}
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.render._
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptor, ValueDescriptor}
import com.intellij.debugger.{DebuggerContext, JavaDebuggerBundle}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiExpression}
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import com.sun.jdi.{ObjectReference, Type, Value}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings

import scala.annotation.nowarn

/**
 * Nikolay.Tropin
 * 2014-10-22
 */
class RuntimeRefRenderer extends ClassRenderer {
  override def getName: String = ScalaBundle.message("scala.runtime.references.renderer")
  override def setName(name: String): Unit = { }
  override def isEnabled: Boolean = ScalaDebuggerSettings.getInstance().DONT_SHOW_RUNTIME_REFS
  override def setEnabled(enabled: Boolean): Unit = {/*see ScalaDebuggerSettingsConfigurable */}
  override def getUniqueId: String = "ScalaRuntimeRefRenderer"

  def isApplicableFor(t: Type): Boolean = {
    t != null && t.name() != null && t.name().startsWith("scala.runtime.") && t.name().endsWith("Ref")
  }

  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit = {
    val descr = unwrappedDescriptor(value, context.getProject)
    autoRenderer(context.getDebugProcess, descr).buildChildren(descr.getValue, builder, context)
  }

  override def isExpandableAsync(value: Value, evaluationContext: EvaluationContext, parentDescriptor: NodeDescriptor): CompletableFuture[lang.Boolean] = {
    val descr = unwrappedDescriptor(value, evaluationContext.getProject)
    val renderer = autoRenderer(evaluationContext.getDebugProcess, descr)
    renderer.isExpandableAsync(descr.getValue, evaluationContext, parentDescriptor)
  }

  override def getChildValueExpression(node: DebuggerTreeNode, context: DebuggerContext): PsiElement = {
    val descr = unwrappedDescriptor(node.getParent.asInstanceOf[ValueDescriptor].getValue, context.getProject)
    val renderer = autoRenderer(context.getDebugProcess, descr)
    renderer.getChildValueExpression(node, context)
  }

  override def calcLabel(descriptor: ValueDescriptor, evaluationContext: EvaluationContext, listener: DescriptorLabelListener): String = {
    val unwrapped = unwrappedDescriptor(descriptor.getValue, evaluationContext.getProject)
    autoRenderer(evaluationContext.getDebugProcess, unwrapped) match {
      case _: ToStringRenderer =>
        calcToStringLabel(descriptor, unwrapped.getValue, evaluationContext.createEvaluationContext(unwrapped.getValue), listener)
      case r =>
        r.calcLabel(unwrapped, evaluationContext, listener)
    }
  }

  override def calcIdLabel(descriptor: ValueDescriptor, process: DebugProcess, labelListener: DescriptorLabelListener): String = {
    val descr = unwrappedDescriptor(descriptor.getValue, process.getProject)
    autoRenderer(process, descr).calcIdLabel(descr, process, labelListener)
  }

  private def autoRenderer(debugProcess: DebugProcess, valueDescriptor: ValueDescriptor) = {
    val unwrappedType = valueDescriptor.getType

    if (isApplicableFor(unwrappedType))
      DebugProcessImpl.getDefaultRenderer(unwrappedType).asInstanceOf[NodeRendererImpl]
    else
      debugProcess.asInstanceOf[DebugProcessImpl].getAutoRenderer(valueDescriptor).asInstanceOf[NodeRendererImpl]: @nowarn("cat=deprecation")
  }
  
  private def unwrappedDescriptor(ref: Value, project: Project) = {
    new ValueDescriptorImpl(project, unwrappedValue(ref)) {
      override def calcValue(evaluationContext: EvaluationContextImpl): Value = getValue

      override def calcValueName(): String = "unwrapped"

      override def getDescriptorEvaluation(context: DebuggerContext): PsiExpression = null
    }
  }

  private def unwrappedValue(ref: Value): Value = {
    ref match {
      case _: ObjectReference =>
        DebuggerUtil.unwrapScalaRuntimeRef(ref) match {
          case v: Value => v
          case _ => ref
        }
      case _ => throw new Exception("Should not unwrap non reference value")
    }
  }

  private def calcToStringLabel(valueDescriptor: ValueDescriptor, value: Value, evaluationContext: EvaluationContext, labelListener: DescriptorLabelListener): String = {
    BatchEvaluator.getBatchEvaluator(evaluationContext.getDebugProcess).invoke(new ToStringCommand(evaluationContext, value) {
      override def evaluationResult(message: String): Unit = {
        valueDescriptor.setValueLabel(StringUtil.notNullize(message))
        labelListener.labelChanged()
      }

      override def evaluationError(message: String): Unit = {
        val msg: String =
          if (value != null) message + " " + JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", value.`type`.name)
          else message
        valueDescriptor.setValueLabelFailed(new EvaluateException(msg, null))
        labelListener.labelChanged()
      }
    })
    XDebuggerUIConstants.getCollectingDataMessage
  }

}
