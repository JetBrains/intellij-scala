package org.jetbrains.plugins.scala.debugger.evaluation
package evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.engine.{DebugProcessImpl, DebuggerUtils, JVMName}
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.StackWalkingThisEvaluator.TypeFilter

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.Try

private[evaluation] final class StackWalkingThisEvaluator(typeName: JVMName, typeFilter: TypeFilter) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): ObjectReference = {
    val frameProxy = context.getFrameProxy
    val threadProxy = frameProxy.threadProxy()
    val frameCount = threadProxy.frameCount()

    @tailrec
    def loop(frameIndex: Int): ObjectReference = {
      if (frameIndex >= frameCount)
        throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.this.not.avalilable"))
      else {
        val stackFrame = threadProxy.frame(frameIndex)
        Try(Option(stackFrame.thisObject()))
          .toOption
          .flatten
          .flatMap(followOuterChain(_, context.getDebugProcess)) match {
          case Some(ref) => ref
          case None => loop(frameIndex + 1)
        }
      }
    }

    loop(frameProxy.getFrameIndex)
  }

  private def filterType(tpe: ReferenceType, debugProcess: DebugProcessImpl): Boolean =
    DebuggerUtils.instanceOf(tpe, typeName.getName(debugProcess)) && (typeFilter match {
      case TypeFilter.ContainsField(name) => tpe.fields().asScala.exists { f =>
        val fieldName = f.name()
        (fieldName ne null) && (fieldName == name || fieldName.endsWith(s"$$$$$name"))
      }
      case TypeFilter.ContainsMethod(name) => tpe.methods().asScala.exists(_.name() == name)
      case TypeFilter.Any => true
    })

  @tailrec
  private def followOuterChain(ref: ObjectReference, debugProcess: DebugProcessImpl): Option[ObjectReference] = {
    val refType = ref.referenceType()
    if (filterType(refType, debugProcess)) Some(ref)
    else {
      refType.fields().asScala.find { f =>
        val name = f.name()
        (name ne null) && name.startsWith("$outer") && f.isFinal && f.isSynthetic && !f.isStatic
      }.map(ref.getValue(_).asInstanceOf[ObjectReference]) match {
        case Some(ref) => followOuterChain(ref, debugProcess)
        case None => None
      }
    }
  }
}

private[evaluation] object StackWalkingThisEvaluator {
  sealed trait TypeFilter
  object TypeFilter {
    final case class ContainsMethod(name: String) extends TypeFilter
    final case class ContainsField(name: String) extends TypeFilter
    case object Any extends TypeFilter
  }
}
