package org.jetbrains.plugins.scala.debugger.evaluation.evaluator;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.expression.Evaluator;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import scala.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: Alefas
 * Date: 17.10.11
 */
public class ScalaNewClassInstanceEvaluator implements Evaluator {
  private final Evaluator myClassTypeEvaluator;
  private final JVMName myConstructorSignature;
  private final Evaluator[] myParamsEvaluators;

  public ScalaNewClassInstanceEvaluator(Evaluator classTypeEvaluator, JVMName constructorSignature, Evaluator[] argumentEvaluators) {
    myClassTypeEvaluator = classTypeEvaluator;
    myConstructorSignature = constructorSignature;
    myParamsEvaluators = argumentEvaluators;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Object obj = myClassTypeEvaluator.evaluate(context);
    if (!(obj instanceof ClassType)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.class.type"));
    }
    ClassType classType = (ClassType)obj;
    // find constructor
    Method method = DebuggerUtilsEx.findMethod(classType, "<init>", myConstructorSignature.getName(debugProcess));
    if (method == null) {
      throw EvaluateExceptionUtil.createEvaluateException(
          JavaDebuggerBundle.message("evaluation.error.cannot.resolve.constructor", myConstructorSignature.getDisplayName(debugProcess)));
    }
    // evaluate arguments
    List<Value> arguments;
    if (myParamsEvaluators != null) {
      arguments = new ArrayList<Value>(myParamsEvaluators.length);
      for (Evaluator evaluator : myParamsEvaluators) {
        Object result = evaluator.evaluate(context);
        if (result instanceof scala.Option) {
          if (((Option) result).isDefined()) {
            arguments.add((Value) result);
          }
        } else {
          arguments.add((Value) result);
        }
      }
    }
    else {
      arguments = Collections.emptyList();
    }
    ObjectReference objRef;
    try {
      objRef = debugProcess.newInstance(context, classType, method, arguments);
    }
    catch (EvaluateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    return objRef;
  }

  public Modifier getModifier() {
    return null;
  }
}
