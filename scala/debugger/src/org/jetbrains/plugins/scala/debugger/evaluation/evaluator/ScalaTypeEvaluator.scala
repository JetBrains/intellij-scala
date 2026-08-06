package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.TypeEvaluator
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}
import com.sun.jdi.ReferenceType
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants.TraitImplementationClassSuffix_211

/**
 * This is a workaround for scala versions > 2.11 (see SCL-10132).
 * It's required because in [[org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil.classnamePostfix]]
 * we build append "$class" for all scala versions, even though it's actual only for 2.11 and earlier.
 */
class ScalaTypeEvaluator(jvmName: JVMName) extends TypeEvaluator(jvmName) {
  override def evaluate(context: EvaluationContextImpl): ReferenceType = {
    try super.evaluate(context)
    catch {
      case e: EvaluateException =>
        val targetExc = e.getExceptionFromTargetVM
        if (targetExc != null && targetExc.referenceType().name == "java.lang.ClassNotFoundException") {
          val debugProcess = context.getDebugProcess
          val name = jvmName.getName(debugProcess)
          if (name.endsWith(TraitImplementationClassSuffix_211)) {
            val nameSince212 = JVMNameUtil.getJVMRawText(name.stripSuffix(TraitImplementationClassSuffix_211))
            new TypeEvaluator(nameSince212).evaluate(context)
          }
          else throw e
        }
        else throw e
    }
  }
}
