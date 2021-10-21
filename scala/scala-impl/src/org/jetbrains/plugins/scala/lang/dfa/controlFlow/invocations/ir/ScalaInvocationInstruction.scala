package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.CustomMethodHandlers.CustomMethodHandler
import com.intellij.codeInspection.dataFlow.interpreter.{DataFlowInterpreter, RunnerResult, StandardDataFlowInterpreter}
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers
import com.intellij.codeInspection.dataFlow.lang.ir.{DfaInstructionState, ExpressionPushingInstruction, SimpleAssignmentInstruction}
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValue, DfaValueFactory}
import com.intellij.codeInspection.dataFlow.{CustomMethodHandlers, DfaCallArguments, MutationSignature}
import com.intellij.psi.{PsiMethod, PsiModifier}
import org.jetbrains.plugins.scala.lang.dfa.analysis.{DummyDfaListener, ScalaDfaAnchor}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport.ClassesSpecialSupport.findSpecialSupportForClasses
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport.CollectionsSpecialSupport.findSpecialSupportForCollections
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport.OtherMethodsSpecialSupport.{CommonMethodsMapping, psiMethodFromText}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ScalaPsiElementTransformer
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{findArgumentsPrimitiveType, scTypeToDfType}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * Intermediate Representation instruction for Scala invocations.
 *
 * Assumes all arguments that the invoked function needs have already been evaluated in a correct order
 * and are present on the top of the stack. It consumes all of those arguments and produces one value
 * on the stack that is the return value of this invocation.
 */
//noinspection UnstableApiUsage
class ScalaInvocationInstruction(invocationInfo: InvocationInfo, invocationAnchor: ScalaDfaAnchor,
                                 exceptionTransfer: Option[DfaControlTransferValue])
  extends ExpressionPushingInstruction(invocationAnchor) {

  override def toString: String = {
    val invokedElementString = invocationInfo.invokedElement
      .map(_.toString)
      .getOrElse("<unknown>")
    s"CALL $invokedElementString"
  }

  override def accept(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState): Array[DfaInstructionState] = {
    implicit val factory: DfaValueFactory = interpreter.getFactory
    val argumentValues = collectArgumentValuesFromStack(stateBefore)
    val methodEffect = findMethodEffect(interpreter, stateBefore, argumentValues)

    if (!methodEffect.isPure) {
      argumentValues.values.foreach(JavaDfaHelpers.dropLocality(_, stateBefore))
      stateBefore.flushFields()
    }

    val returnValue = if (!methodEffect.handledSpecially) {
      tryInterpretExternalMethod(invocationInfo, stateBefore, factory) match {
        case Some(returnValue) => returnValue
        case _ => methodEffect.returnValue.getDfType
      }
    } else methodEffect.returnValue.getDfType

    returnFromInvocation(returnValue, stateBefore, interpreter)
  }

  private def returnFromInvocation(returnValue: DfType, stateBefore: DfaMemoryState,
                                   interpreter: DataFlowInterpreter): Array[DfaInstructionState] = {
    val exceptionalState = stateBefore.createCopy()
    val exceptionalResult = exceptionTransfer.map(_.dispatch(exceptionalState, interpreter).asScala)
      .getOrElse(Nil)

    val normalResult = returnValue match {
      case DfType.BOTTOM => None
      case _ => pushResult(interpreter, stateBefore, returnValue)
        Some(nextState(interpreter, stateBefore))
    }

    (exceptionalResult ++ normalResult).toArray
  }

  // TODO refactor a lot, extract to a new class
  // TODO add evaluated arguments to the stack
  // TODO add limitations on depth, size, recursion etc. + add a flag to not report anything in an nested call
  private def tryInterpretExternalMethod(invocationInfo: InvocationInfo, stateBefore: DfaMemoryState,
                                         factory: DfaValueFactory): Option[DfType] = {
    invocationInfo.invokedElement match {
      case Some(InvokedElement(function: ScFunctionDefinition))
        if supportsInterproceduralAnalysis(function) => function.body match {
        case Some(body) => analyseExternalMethodBody(body, stateBefore, factory)
        case _ => None
      }
      case _ => None
    }
  }

  private def supportsInterproceduralAnalysis(function: ScFunctionDefinition): Boolean = {
    function.hasModifierPropertyScala(PsiModifier.FINAL) || function.hasModifierPropertyScala(PsiModifier.PRIVATE)
  }

  private def analyseExternalMethodBody(body: ScExpression, stateBefore: DfaMemoryState,
                                        factory: DfaValueFactory): Option[DfType] = {
    val controlFlowBuilder = new ScalaDfaControlFlowBuilder(factory, body)
    new ScalaPsiElementTransformer(body).transform(controlFlowBuilder)

    val resultDestination = factory.getVarFactory.createVariableValue(MethodResultDescriptor())
    val flow = controlFlowBuilder.buildAndReturn(resultDestination)

    val listener = new DummyDfaListener
    val interpreter = new StandardDataFlowInterpreter(flow, listener)

    if (interpreter.interpret(stateBefore) != RunnerResult.OK) None
    else Some(stateBefore.getDfType(resultDestination))
  }

  private def unknownValue(implicit factory: DfaValueFactory): DfaValue = factory.fromDfType(DfType.TOP)

  private def collectArgumentValuesFromStack(stateBefore: DfaMemoryState): Map[Argument, DfaValue] = {
    invocationInfo.argListsInEvaluationOrder.flatten
      .reverseIterator
      .map((_, stateBefore.pop()))
      .toMap
  }

  private def findMethodEffect(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState, argumentValues: Map[Argument, DfaValue])
                              (implicit factory: DfaValueFactory): MethodEffect = {
    invocationInfo.invokedElement match {
      case None => MethodEffect(unknownValue, isPure = false, handledSpecially = false)
      case Some(invokedElement) =>
        implicit val projectContext: ProjectContext = invokedElement.psiElement.getProject

        val specialHandler = findArgumentsPrimitiveType(argumentValues)
          .flatMap { argumentsType =>
            invokedElement.qualifiedName
              .flatMap(CommonMethodsMapping.get(_, argumentsType))
              .flatMap(psiMethodFromText)
              .map(method => (method, Option(CustomMethodHandlers.find(method))))
          }

        val specialMethodEffect = specialHandler match {
          case Some((method, Some(handler))) => Some(findMethodEffectWithJavaCustomHandler(stateBefore,
            argumentValues, handler, method))
          case _ => None
        }

        specialMethodEffect.getOrElse {
          invokedElement.psiElement match {
            case psiMethod: PsiMethod => Option(CustomMethodHandlers.find(psiMethod)) match {
              case Some(handler) => findMethodEffectWithJavaCustomHandler(stateBefore,
                argumentValues, handler, psiMethod)
              case _ => findMethodEffectForScalaMethod(interpreter, stateBefore, argumentValues)
            }
            case _ => findMethodEffectForScalaMethod(interpreter, stateBefore, argumentValues)
          }
        }
    }
  }

  private def findMethodEffectForScalaMethod(interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState,
                                             argumentValues: Map[Argument, DfaValue])
                                            (implicit factory: DfaValueFactory): MethodEffect = {
    val returnType = invocationInfo.invokedElement
      .map(element => scTypeToDfType(element.returnType))
      .getOrElse(DfType.TOP)

    val classesEnhancement = findSpecialSupportForClasses(invocationInfo, argumentValues) match {
      case Some((classParamValues, methodEffect)) => assignClassParameterValues(classParamValues, interpreter, stateBefore)
        Some(methodEffect)
      case _ => None
    }
    val collectionsEnhancement = findSpecialSupportForCollections(invocationInfo, argumentValues, stateBefore)

    val enhancement = classesEnhancement.orElse(collectionsEnhancement)
    enhancement.map(enhanceReturnType(returnType, _))
      .getOrElse(MethodEffect(factory.fromDfType(returnType), isPure = false, handledSpecially = false))
  }

  private def enhanceReturnType(returnType: DfType, methodEffect: MethodEffect)
                               (implicit factory: DfaValueFactory): MethodEffect = {
    val enhancedType = methodEffect.returnValue.getDfType.meet(returnType)
    methodEffect.copy(returnValue = factory.fromDfType(enhancedType))
  }

  private def assignClassParameterValues(classParameterValues: Map[ScClassParameter, DfaValue],
                                         interpreter: DataFlowInterpreter, stateBefore: DfaMemoryState)
                                        (implicit factory: DfaValueFactory): Unit = {
    classParameterValues.foreach { case (parameter, value) =>
      val dfaVariable = factory.getVarFactory.createVariableValue(ScalaDfaVariableDescriptor(parameter, parameter.isStable))
      stateBefore.push(value)
      val assignment = new SimpleAssignmentInstruction(null, dfaVariable)
      assignment.accept(interpreter, stateBefore)
    }
  }

  private def findMethodEffectWithJavaCustomHandler(stateBefore: DfaMemoryState,
                                                    argumentValues: Map[Argument, DfaValue],
                                                    handler: CustomMethodHandler, psiMethod: PsiMethod)
                                                   (implicit factory: DfaValueFactory): MethodEffect = {
    val properArgumentValues = invocationInfo.properArguments.flatten
      .map(argumentValues.getOrElse(_, unknownValue))
    val thisArgumentValue = invocationInfo.thisArgument
      .flatMap(argumentValues.get).getOrElse(unknownValue)

    val mutationSignature = MutationSignature.fromMethod(psiMethod)
    val dfaCallArguments = new DfaCallArguments(thisArgumentValue, properArgumentValues.toArray, mutationSignature)
    val dfaReturnValue = Option(handler.getMethodResultValue(dfaCallArguments, stateBefore, factory, psiMethod))

    val returnValue = dfaReturnValue.getOrElse(unknownValue)
    val isPure = mutationSignature.isPure && !JavaDfaHelpers.mayLeakFromType(returnValue.getDfType)
    MethodEffect(returnValue, isPure, handledSpecially = returnValue != unknownValue)
  }
}
