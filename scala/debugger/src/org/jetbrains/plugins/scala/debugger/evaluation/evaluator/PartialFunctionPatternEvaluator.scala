package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.sun.jdi.{ClassType, Value}
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

/**
 * Given a partial function, produces a single value from the parameters of the current stack frame which can then be
 * broken down in a pattern match. This can be a single value if there is only one parameter, or a tuple.
 */
private[evaluation] class PartialFunctionPatternEvaluator extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value = {
    if (inPartialFunctionApplyOrElse(context)) {
      // In an actual PartialFunction#applyOrElse, always return the first argument of the method.
      val frame = context.getFrameProxy
      frame.getStackFrame.getArgumentValues.get(0)
    } else if (inAbstractClassSamType(context)) {
      // In an `abstract class` SAM type implemented using a partial function,
      // find the compiler generated method arguments and combine them in a tuple (if more than 1).
      // Otherwise return the one synthetic argument.
      evaluateSyntheticArguments(context)(_.take)
    } else {
      // In a `scala.FunctionN` eimplemented using partial function syntax, or trait SAM type.
      // Same situation as `abstract class` SAM, except the synthetic arguments are found at the end
      // of the argument list, instead of the beginning.
      evaluateSyntheticArguments(context)(_.takeRight)
    }
  }

  /**
   * Extracts the synthetic arguments from the current frame and returns them as a tuple (if more than 1) or just the
   * value if exactly 1.
   */
  private def evaluateSyntheticArguments(context: EvaluationContextImpl)(takeFn: Seq[Value] => Int => Seq[Value]): Value = {
    val frame = context.getFrameProxy
    val location = frame.location()
    val method = location.method()
    val syntheticArgumentsCount = countSyntheticArguments(method.arguments().asScala.map(_.name()))
    val syntheticArgumentsValues = takeFn(frame.getArgumentValues.asScala.toSeq)(syntheticArgumentsCount)

    syntheticArgumentsCount match {
      case 0 => throw EvaluationException(DebuggerBundle.message("error.no.synthetic.arguments.found"))
      case n if n > 22 => throw EvaluationException(DebuggerBundle.message("error.cannot.evaluate.more.than.22.synthetic.arguments"))
      case 1 => syntheticArgumentsValues.head
      case n =>
        val tupleClass = context.getDebugProcess.findClass(context, s"scala.Tuple$n", context.getClassLoader).asInstanceOf[ClassType]
        val constructor = tupleClass.methodsByName("<init>").get(0)
        val boxed = syntheticArgumentsValues.map(v => ScalaBoxingEvaluator.box(v, context)).map(_.asInstanceOf[Value])
        context.getDebugProcess.newInstance(context, tupleClass, constructor, boxed.asJava)
    }
  }

  /**
   * In higher order functions that expect a `scala.PartialFunction`, the compiler compiles the source code into
   * `scala.runtime.AbstractPartialFunction#applyOrElse`. This method always has 2 arguments, the first one is always
   * the partial function argument. Multiple partial function arguments are simulated using a tuple.
   */
  private def inPartialFunctionApplyOrElse(context: EvaluationContextImpl): Boolean = {
    val location = context.getFrameProxy.location()
    val method = location.method()

    method.declaringType() match {
      case ct: ClassType =>
        ct.superclass().name().startsWith("scala.runtime.AbstractPartialFunction") && method.name().startsWith("applyOrElse")
      case _ => false
    }
  }

  /**
   * This is a special case treated slightly differently by the compiler, in terms of the output bytecode, and that's
   * why it needs to be handled separately.
   *
   * An implementation of an `abstract class` SAM type is an actual class. It cannot be implemented using
   * `invokedynamic` instructions. In the generated class, there is a bridge method that ultimately calls the method
   * where the partial function syntax is compiled into.
   *
   * In this method, we're starting from our partial function, we're traversing the stack 1 frame below the current one,
   * in order to find the bridge method. That bridge method needs to be the SAM. The declaring type is checked for
   * exactly 1 abstract method.
   *
   * On the other hand, SAM types that are traits are implemented using `invokedynamic` just like regular lambdas.
   */
  private def inAbstractClassSamType(context: EvaluationContextImpl): Boolean = {
    val currentFrame = context.getFrameProxy
    val thread = currentFrame.threadProxy()
    val index = currentFrame.getFrameIndex

    val currentMethod = currentFrame.location().method()
    if (!currentMethod.isSynthetic) return false

    val locationBelow = thread.frame(index + 1).location()
    val declaringType = locationBelow.method().declaringType()

    declaringType match {
      case ct: ClassType =>
        val superClass = ct.superclass()
        val allMethods = superClass.allMethods()
        superClass.isAbstract && allMethods.asScala.count(_.isAbstract) == 1
      case _ => false
    }
  }

  /**
   * Returns the number of consecutive synthetic arguments.
   */
  private def countSyntheticArguments(names: Iterable[String]): Int = {
    var result = 0
    var startedCounting = false
    val iterator = names.iterator

    while (iterator.hasNext) {
      val arg = iterator.next()
      arg match {
        case syntheticArgumentName(index) if result == index.toInt =>
          startedCounting = true
          result += 1
        case _ =>
          if (startedCounting) {
            return result
          }
      }
    }

    result
  }

  // x0$1, x1$1, x2$1 etc...
  private val syntheticArgumentName: Regex = "x(\\d+)\\$1".r
}
