package org.jetbrains.plugins.scala.debugger.ui

import java.util

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.ui.impl.watch.{MessageDescriptor, NodeManagerImpl}
import com.intellij.debugger.ui.tree.DebuggerTreeNode
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, ClassRenderer}
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
class ScalaClassWithObscureFieldsRenderer extends ClassRenderer {
  override def isApplicable(t: Type): Boolean = {

    if (!super.isApplicable(t)) return false

    t match {
      case refType: ReferenceType if DebuggerUtil.isScala(refType) =>
        refType.allFields().asScala.map(_.name).exists(isObscureName)
      case _ => false
    }
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean = {
    val default = super.shouldDisplay(context, objInstance, field)
    val isInternal = field.name().startsWith("bitmap$")
    default && !isInternal
  }

  override def buildChildren(value: Value, builder: ChildrenBuilder, evaluationContext: EvaluationContext): Unit = {
    DebuggerManagerThreadImpl.assertIsManagerThread()

    val objRef = value match {
      case o: ObjectReference => o
      case _ => return
    }

    val nodeManager = builder.getNodeManager
    val nodeDescriptorFactory = builder.getDescriptorManager
    val fields = objRef.referenceType().allFields().asScala
    if (fields.isEmpty) {
      builder.setChildren(util.Collections.singletonList(nodeManager.createMessageNode(MessageDescriptor.CLASS_HAS_NO_FIELDS.getLabel)))
      return
    }

    val children = new util.ArrayList[DebuggerTreeNode]()
    for {
      field <- fields
      fieldName = field.name
      if shouldDisplay(evaluationContext, objRef, field)
    } {
      var fieldDescriptor = nodeDescriptorFactory.getFieldDescriptor(builder.getParentDescriptor, objRef, field)
      if (isObscureName(fieldName)) {
        fieldDescriptor = new ScalaObscureFieldDescriptor(fieldDescriptor, evaluationContext.getProject)
      }
      children.add(nodeManager.createNode(fieldDescriptor, evaluationContext))
    }

    if (XDebuggerSettingsManager.getInstance.getDataViewSettings.isSortValues) {
      children.sort(NodeManagerImpl.getNodeComparator)
    }

    builder.setChildren(children)
  }

  private def isObscureName(s: String) = s != "$outer" && s.contains("$")
}
