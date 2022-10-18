package org.jetbrains.plugins.scala
package debugger.ui

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContext}
import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl}
import com.intellij.debugger.ui.tree.render.{ChildrenBuilder, DescriptorLabelListener}
import com.intellij.debugger.ui.tree.{NodeDescriptor, ValueDescriptor}
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.debugger.ui.util._

import java.util.concurrent.CompletableFuture

class ScalaRuntimeRefRenderer extends ScalaClassRenderer {
  import ScalaRuntimeRefRenderer._

  override def getName: String = DebuggerBundle.message("scala.runtime.ref.renderer")

  //noinspection ApiStatus,UnstableApiUsage
  setIsApplicableChecker(tpe => CompletableFuture.completedFuture(isApplicableFor(tpe)))

  // most Scala runtime refs are defined as Java classes
  override def isApplicableFor(tpe: Type): Boolean = tpe match {
    case ct: ClassType if ScalaDebuggerSettings.getInstance().DONT_SHOW_RUNTIME_REFS && isRuntimeRef(ct) => true
    case _ => false
  }

  override def shouldDisplay(context: EvaluationContext, objInstance: ObjectReference, field: Field): Boolean = false

  override def calcLabel(descriptor: ValueDescriptor, context: EvaluationContext, listener: DescriptorLabelListener): String = {
    onDebuggerManagerThread(context)(unwrapValue(descriptor.getValue).toString).attempt.flatTap {
      case Left(t) =>
        onDebuggerManagerThread(context) {
          descriptor.setValueLabelFailed(evaluationExceptionFromThrowable(t))
          listener.labelChanged()
        }
      case Right(label) =>
        onDebuggerManagerThread(context) {
          descriptor.setValueLabel(label)
          listener.labelChanged()
        }
    }.rethrow.getNow(XDebuggerUIConstants.getCollectingDataMessage)
  }

  override def buildChildren(value: Value, builder: ChildrenBuilder, context: EvaluationContext): Unit =
    for {
      unwrapped <- onDebuggerManagerThread(context)(unwrapValue(value))
      renderer <- onDebuggerManagerThread(context)(context.getDebugProcess.asInstanceOf[DebugProcessImpl].getAutoRendererAsync(unwrapped.`type`())).flatten
      _ <- onDebuggerManagerThread(context)(renderer.buildChildren(unwrapped, builder, context))
    } yield ()

  override def isExpandableAsync(value: Value, context: EvaluationContext, descriptor: NodeDescriptor): CompletableFuture[java.lang.Boolean] =
    for {
      unwrapped <- onDebuggerManagerThread(context)(unwrapValue(value))
      renderer <- onDebuggerManagerThread(context)(context.getDebugProcess.asInstanceOf[DebugProcessImpl].getAutoRendererAsync(unwrapped.`type`())).flatten
      result <- onDebuggerManagerThread(context)(renderer.isExpandableAsync(unwrapped, context, descriptor)).flatten
    } yield result

  override def calcIdLabel(descriptor: ValueDescriptor, process: DebugProcess, labelListener: DescriptorLabelListener): String =
    descriptor.getType match {
      case RefType(refType) =>
        s"unwrapped Scala runtime $refType reference"
      case _ => super.calcIdLabel(descriptor, process, labelListener)
    }

  private def unwrapValue(ref: Value): Value = {
    ref match {
      case _: ObjectReference =>
        DebuggerUtil.unwrapScalaRuntimeRef(ref) match {
          case v: Value => v
          case _ => ref
        }
      case _ => throw new EvaluateException("Should not unwrap non reference value")
    }
  }
}

private object ScalaRuntimeRefRenderer {
  def isRuntimeRef(ct: ClassType): Boolean = {
    val name = ct.name()
    name.startsWith("scala.runtime") && name.endsWith("Ref") && name != "scala.runtime.LazyRef"
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
