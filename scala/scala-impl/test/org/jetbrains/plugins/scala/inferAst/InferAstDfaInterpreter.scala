package org.jetbrains.plugins.scala.inferAst

import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ThisDescriptor
import com.intellij.codeInspection.dataFlow.lang.DfaListener
import com.intellij.codeInspection.dataFlow.lang.ir.{ControlFlow, DfaInstructionState, ReturnInstruction}
import com.intellij.codeInspection.dataFlow.types.{DfType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaVariableValue, VariableDescriptor}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.{ScalaDfaAnchor, ScalaDfaAnchorWithPsiElement}
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.ScalaInvocationInstruction
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaObjectVariableDescriptor, ScalaDfaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.ThisArgument
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import scala.collection.mutable
import scala.jdk.CollectionConverters.IteratorHasAsScala

class InferAstDfaInterpreter(cfg: ControlFlow, thisObject: ScObject) extends StandardDataFlowInterpreter(cfg, DfaListener.EMPTY) {
  private val _resultingStates = mutable.Buffer.empty[InferAstDfaMemoryState]

  def resultingStates: Seq[InferAstDfaMemoryState] = _resultingStates.toSeq

  override def acceptInstruction(instructionState: DfaInstructionState): Array[DfaInstructionState] = {
    val instruction = instructionState.getInstruction
    val index = instruction.getIndex
    val memoryState = instructionState.getMemoryState.asInstanceOf[InferAstDfaMemoryState]

    //println(s"Processing instruction $index (${memoryState.getStackSize}): ${instruction.toString}")

    instruction match {
      case invocation: ScalaInvocationInstruction =>
        val invokedName = invocation.invocationInfo.invokedElement
          .orElse(invocation.getDfaAnchor.asOptionOf[ScalaDfaAnchorWithPsiElement].map(_.psiElement).collect {
            case call: MethodInvocation => call.getInvokedExpr.getText
          })
          .getOrElse(throw new Exception("Cannot process call to unknown method"))
          .toString

        knownFunctions.get(invokedName) match {
          case Some(effect) =>
            val after = effect(memoryState, index)
            Array(new DfaInstructionState(getInstruction(index + 1), after))
          case None if ignoredFunctions.contains(invokedName) => super.acceptInstruction(instructionState)
          case None =>
            val args = invocation.collectArgumentValuesFromStack(memoryState)(getFactory)
            val thisValue = args
              .collectFirst { case (arg, value) if arg.kind == ThisArgument => value }
              .getOrElse(throw new Exception(s"Cannot process call to unknown method $invokedName"))

            val called = extractEqsValue(memoryState, thisValue) {
              case obj: ScalaDfaObjectVariableDescriptor => obj.obj
              case _: ThisDescriptor => thisObject
            }

            val method = invocation.invocationInfo.invokedElement.get.psiElement.asInstanceOf[ScFunction]
            called match {
              case Some(called: ScObject) =>
                val realFunction = method match {
                  case fun: ScFunctionDefinition =>
                    fun
                  case _ =>
                    val Seq(fun) = called.allMethods
                      .filter(_.method.findSuperMethods(method.containingClass).contains(method))
                      .flatMap(_.method.asOptionOf[ScFunctionDefinition])
                      .toSeq
                    fun
                }
                if (method.returnType.exists(_.isBoolean)) {
                  val trueAction = AstAction.Call(AnalysisItem(realFunction, called, Seq.empty), Some(true))
                  val falseAction = trueAction.copy(result = Some(false))

                  val trueState = memoryState.createCopy()
                  val falseState = memoryState
                  trueState.addAction(index, trueAction)
                  falseState.addAction(index, falseAction)
                  trueState.push(getFactory.fromDfType(DfTypes.TRUE))
                  falseState.push(getFactory.fromDfType(DfTypes.FALSE))
                  Array(
                    new DfaInstructionState(getInstruction(index + 1), trueState),
                    new DfaInstructionState(getInstruction(index + 1), falseState)
                  )
                } else {
                  val item = AstAction.Call(AnalysisItem(realFunction, called, Seq.empty), None)
                  memoryState.addAction(index, item)
                  memoryState.push(getFactory.getUnknown)
                  Array(new DfaInstructionState(getInstruction(index + 1), memoryState))
                }
              case _ =>
                throw new Exception(s"Cannot process method $invokedName")
            }

          //case None => throw new Exception(s"Cannot process call to $invokedName")
        }
      case _: ReturnInstruction =>
        _resultingStates += memoryState
        val result = super.acceptInstruction(instructionState)

        assert(result.isEmpty, "If this fails the return logic has to be reworked :) have fun")
        result
      case _ =>
        super.acceptInstruction(instructionState)
    }
  }

  private val currentTokenVariable = cfg.getFactory.getVarFactory.createVariableValue(CurrentTokenDescriptor)

  private val ignoredFunctions: Set[String] = Set()
  private val knownFunctions: Map[String, (InferAstDfaMemoryState, Int) => InferAstDfaMemoryState] = Map(
    "SyntaxTreeBuilder#advanceLexer" -> `SyntaxTreeBuilder#advanceLexer`,
    "PsiBuilder#mark" -> `PsiBuilder#mark`,
    "Marker#done" -> `Marker#done`,
    "Marker#rollbackTo" -> `Marker#rollbackTo`,
    "Marker#drop" -> `Marker#drop`,
    "SyntaxTreeBuilder#getTokenType" -> `SyntaxTreeBuilder#getTokenType`,
    "ScalaBundle#message" -> `ScalaBundle#message`,
    "SyntaxTreeBuilder#error" -> `SyntaxTreeBuilder#error`,
  )

  private def extractEqsValue[T](state: InferAstDfaMemoryState, value: DfaValue)(f: PartialFunction[VariableDescriptor, T]): Option[T] = {
    extractEqs(state, value).collect(f) match {
      case Seq(value) => Some(value)
      case _ =>
        value match {
          case v: DfaVariableValue => f.lift(v.getDescriptor)
          case _ => None
        }
    }
  }

  private def extractEqs(state: InferAstDfaMemoryState, value: DfaValue): Seq[VariableDescriptor] = {
    val idx = state.getEqClassIndex(value)
    if (idx >= 0) {
      val eqClass = state.getEqClasses.get(idx)
      eqClass.iterator().asScala.map(_.getDescriptor).toSeq
    } else Seq.empty
  }

  private def `SyntaxTreeBuilder#advanceLexer`(state: InferAstDfaMemoryState, index: Int): InferAstDfaMemoryState = {
    state.pop() // builder

    val action = extractEqsValue(state, currentTokenVariable) {
      case v: ScalaDfaVariableDescriptor if v.toString.startsWith("ScalaTokenType") => v.toString
    }

    state.addAction(index, AstAction.Token(action.getOrElse("unknown")))

    state.push(getFactory.getUnknown)
    state.flushVariable(currentTokenVariable)
    state
  }

  private def `PsiBuilder#mark`(state: InferAstDfaMemoryState, index: Int): InferAstDfaMemoryState = {
    state.pop() // builder
    state.addAction(index, AstAction.Mark(index))
    state.push(getFactory.getVarFactory.createVariableValue(MarkerDescriptor(index)))
    state
  }

  private def `Marker#done`(state: InferAstDfaMemoryState, index: Int): InferAstDfaMemoryState = {
    val elementType = state.pop()
    val marker = state.pop()

    val actions = extractEqsValue(state, marker) {
      case MarkerDescriptor(index) => AstAction.Done(index, elementType.toString)
    }

    assert(actions.isDefined, s"Cannot find marker $marker")

    state.addActions(index, actions)

    state.push(getFactory.getUnknown)
    state
  }


  private def `Marker#rollbackTo`(state: InferAstDfaMemoryState, index: Int): InferAstDfaMemoryState = {
    val marker = state.pop()

    val actions = extractEqs(state, marker).collect {
      case MarkerDescriptor(index) => AstAction.Rollback(index)
    }

    state.addActions(index, actions)

    state.push(getFactory.getUnknown)
    state
  }

  private def `Marker#drop`(state: InferAstDfaMemoryState, index: Int): InferAstDfaMemoryState = {
    val marker = state.pop()

    val actions = extractEqs(state, marker).collect {
      case MarkerDescriptor(index) => AstAction.Drop(index)
    }

    state.addActions(index, actions)

    state.push(getFactory.getUnknown)
    state
  }

  private def `SyntaxTreeBuilder#getTokenType`(state: InferAstDfaMemoryState, index: Int): InferAstDfaMemoryState = {
    state.pop() // builder
    state.push(currentTokenVariable)
    state
  }

  private def `ScalaBundle#message`(state: InferAstDfaMemoryState, index: Int): InferAstDfaMemoryState = {
    val error = state.pop()
    state.pop() // ScalaBundle
    state.push(error)
    state
  }

  private def `SyntaxTreeBuilder#error`(state: InferAstDfaMemoryState, index: Int): InferAstDfaMemoryState = {
    val error = state.pop()
    state.pop() // builder
    val errorString = Option(error.getDfType.getConstantOfType(classOf[String]))
    state.addAction(index, AstAction.Error(errorString.get))
    state.push(getFactory.getUnknown)
    state
  }
}
