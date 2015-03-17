package org.jetbrains.plugins.scala
package debugger.ui

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.{DebugProcessImpl, DebugProcess}
import com.intellij.debugger.engine.evaluation.{EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, DescriptorLabelListener, NodeRendererImpl}
import com.intellij.debugger.ui.tree.{DebuggerTreeNode, NodeDescriptor, ValueDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.sun.jdi.{ObjectReference, Type, Value}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings

/**
 * Nikolay.Tropin
 * 2014-10-22
 */
class RuntimeRefRenderer extends NodeRendererImpl {

  override def getName = "Scala runtime references renderer"
  override def setName(name: String) { }
  override def isEnabled: Boolean = ScalaDebuggerSettings.getInstance().DONT_SHOW_RUNTIME_REFS
  override def setEnabled(enabled: Boolean) {/*see ScalaDebuggerSettingsConfigurable */}
  override def getUniqueId: String = "ScalaRuntimeRefRenderer"

  override def isApplicable(t: Type): Boolean = {
    t.name() != null && t.name().startsWith("scala.runtime.") && t.name().endsWith("Ref")
  }
  
  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit = {
    val descr = unwrappedDescriptor(value, context.getProject)
    delegateRenderer(context.getDebugProcess, descr).buildChildren(descr.getValue, builder, context)
  }

  override def isExpandable(value: Value, evaluationContext: EvaluationContext, parentDescriptor: NodeDescriptor): Boolean = {
    val descr = unwrappedDescriptor(value, evaluationContext.getProject)
    val renderer = delegateRenderer(evaluationContext.getDebugProcess, descr)
    renderer.isExpandable(descr.getValue, evaluationContext, parentDescriptor)
  }

  override def getChildValueExpression(node: DebuggerTreeNode, context: DebuggerContext): PsiExpression = {
    val descr = unwrappedDescriptor(node.getParent.asInstanceOf[ValueDescriptor].getValue, context.getProject)
    val renderer = delegateRenderer(context.getDebugProcess, descr)
    renderer.getChildValueExpression(node, context)
  }

  override def calcLabel(descriptor: ValueDescriptor, evaluationContext: EvaluationContext, listener: DescriptorLabelListener): String = {
    val descr = unwrappedDescriptor(descriptor.getValue, evaluationContext.getProject)
    val renderer = delegateRenderer(evaluationContext.getDebugProcess, descr)
    renderer.calcLabel(descr, evaluationContext, listener)
  }


  override def getIdLabel(value: Value, process: DebugProcess): String = {
    val descr = unwrappedDescriptor(value, process.getProject)
    delegateRenderer(process, descr).getIdLabel(descr.getValue, process)
  }

  private def delegateRenderer(debugProcess: DebugProcess, valueDescriptor: ValueDescriptor) = {
    debugProcess.asInstanceOf[DebugProcessImpl].getAutoRenderer(valueDescriptor).asInstanceOf[NodeRendererImpl]
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
      case obj: ObjectReference =>
        val field = obj.referenceType().fieldByName("elem")
        obj.getValue(field)
      case _ => throw new Exception("Should not unwrap non reference value")
    }
  }

}
