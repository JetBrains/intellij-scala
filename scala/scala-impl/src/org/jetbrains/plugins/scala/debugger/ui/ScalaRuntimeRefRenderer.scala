package org.jetbrains.plugins.scala
package debugger.ui

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.ui.tree.{NodeDescriptor, ValueDescriptor}
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, DescriptorLabelListener}
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

import java.util.concurrent.CompletableFuture

class ScalaRuntimeRefRenderer extends ScalaClassRenderer {
  import ScalaRuntimeRefRenderer._

  override def getName: String = ScalaBundle.message("scala.runtime.ref.renderer")

  //noinspection ApiStatus,UnstableApiUsage
  setIsApplicableChecker(tpe => CompletableFuture.completedFuture(isApplicableFor(tpe)))

  // most Scala runtime refs are defined as Java classes
  override def isApplicableFor(tpe: Type): Boolean = tpe match {
    case ct: ClassType if isRuntimeRef(ct) => true
    case _ => false
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean = false

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, labelListener: DescriptorLabelListener): String =
    descriptor.getType match {
      case RefType(_) =>
        DebuggerUtil.unwrapScalaRuntimeRef(descriptor.getValue).toString
      case _ => super.calcLabel(descriptor, context, labelListener)
    }

  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit = ()

  override def isExpandableAsync(value: Value, context: EvaluationContext, descriptor: NodeDescriptor): CompletableFuture[java.lang.Boolean] =
    CompletableFuture.completedFuture(false)

  override def calcIdLabel(descriptor: ValueDescriptor, process: DebugProcess, labelListener: DescriptorLabelListener): String =
    descriptor.getType match {
      case RefType(refType) =>
        s"unwrapped Scala runtime $refType reference"
      case _ => super.calcIdLabel(descriptor, process, labelListener)
    }
}

private object ScalaRuntimeRefRenderer {
  def isRuntimeRef(ct: ClassType): Boolean = {
    val name = ct.name()
    name.startsWith("scala.runtime") && name.endsWith("Ref")
  }

  object RefType {
    def unapply(tpe: Type): Option[String] = {
      val name = tpe.name().replace("scala.runtime.", "").replace("Ref", "")
      if (name.startsWith("Volatile"))
        Some(s"volatile ${name.replace("Volatile", "")}")
      else
        Some(name)
    }
  }
}
