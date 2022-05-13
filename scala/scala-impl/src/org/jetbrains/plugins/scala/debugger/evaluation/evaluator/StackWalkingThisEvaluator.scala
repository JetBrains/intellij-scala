package org.jetbrains.plugins.scala.debugger.evaluation
package evaluator

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * Walks the stack frames searching for a `this` object reference that matches the given predicate. It also follows
 * a chain of `$outer` fields, which is how the Scala compiler captures references of outer contexts.
 */
private final class StackWalkingThisEvaluator private(predicate: ReferenceType => Boolean) extends ValueEvaluator {
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
          .flatMap(followOuterChain) match {
          case Some(ref) => ref
          case None => loop(frameIndex + 1)
        }
      }
    }

    loop(frameProxy.getFrameIndex)
  }

  @tailrec
  private def followOuterChain(ref: ObjectReference): Option[ObjectReference] = {
    val refType = ref.referenceType()
    if (predicate(refType)) Some(ref)
    else {
      refType.fields().asScala.find { f =>
        val name = f.name()
        (name ne null) && name.startsWith("$outer") && f.isFinal && f.isSynthetic && !f.isStatic
      }.map(ref.getValue(_).asInstanceOf[ObjectReference]) match {
        case Some(null) => None
        case Some(ref) => followOuterChain(ref)
        case None => None
      }
    }
  }
}

private[evaluation] object StackWalkingThisEvaluator {

  private val Any: ReferenceType => Boolean = _ => true

  private def typePredicate(debuggerTypeName: String): ReferenceType => Boolean =
    DebuggerUtils.instanceOf(_, debuggerTypeName)

  private def fieldPredicate(debuggerTypeName: String, fieldName: String): ReferenceType => Boolean = { tpe =>
    DebuggerUtils.instanceOf(tpe, debuggerTypeName) && tpe.allFields().asScala.exists { f =>
      val name = f.name()
      (name == fieldName) || name.endsWith(s"$$$$$fieldName")
    }
  }

  private def methodPredicate(debuggerTypeName: String, methodName: String): ReferenceType => Boolean = { tpe =>
    DebuggerUtils.instanceOf(tpe, debuggerTypeName) && tpe.allMethods().asScala.exists(_.name() == methodName)
  }

  private[evaluation] def closest: ValueEvaluator = new StackWalkingThisEvaluator(Any)

  private[evaluation] def ofType(debuggerTypeName: String): ValueEvaluator =
    new StackWalkingThisEvaluator(typePredicate(debuggerTypeName))

  private[evaluation] def withField(debuggerTypeName: String, fieldName: String): ValueEvaluator =
    new StackWalkingThisEvaluator(fieldPredicate(debuggerTypeName, fieldName))

  private[evaluation] def withMethod(debuggerTypeName: String, methodName: String): ValueEvaluator =
    new StackWalkingThisEvaluator(methodPredicate(debuggerTypeName, methodName))
}
