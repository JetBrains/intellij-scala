package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import com.intellij.codeInspection.dataFlow.java.JavaClassDef
import com.intellij.codeInspection.dataFlow.java.inst.{BooleanBinaryInstruction, EnsureIndexInBoundsInstruction, JvmPushInstruction, NotInstruction, NumericBinaryInstruction, PrimitiveConversionInstruction, ThrowInstruction}
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker
import com.intellij.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem
import com.intellij.codeInspection.dataFlow.lang.{DfaAnchor, UnsatisfiedConditionProblem}
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.{ControlFlowOffset, DeferredOffset, FixedOffset}
import com.intellij.codeInspection.dataFlow.lang.ir._
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaControlTransferValue, DfaValueFactory, DfaVariableValue, RelationType}
import com.intellij.psi.{CommonClassNames, PsiElement, PsiPrimitiveType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.BooleanExt
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.{ScalaDfaAnchor, ScalaStatementAnchor}
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.ScalaInvocationInstruction
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.interprocedural.AnalysedMethodInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaVariableDescriptor
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.InstructionBuilder._
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.annotation.tailrec
import scala.collection.mutable

abstract class InstructionBuilder(factory: DfaValueFactory,
                                  context: ScalaPsiElement)
{
  private val flow = new ControlFlow(factory, context)
  private val trapTracker = new TrapTracker(factory, JavaClassDef.typeConstraintFactory(context))
  private val stack = new StackManager
  private val labelStacks = mutable.Map.empty[Int, VStack]

  private def addInstruction(instruction: Instruction): Unit = flow.addInstruction(instruction)

  private def addPoppingInstruction(v: StackValue, instruction: Instruction): Unit = {
    stack.pop(v)
    addInstruction(instruction)
  }

  private def addPushingInstruction(instruction: Instruction): StackValue = {
    val value = stack.push(flow.getInstructionCount)
    addInstruction(instruction)
    value
  }

  private def addIncomingStack(label: Label, stack: VStack): Unit = {
    val index = label match {
      case label: DeferredLabel =>
       label.stacksOrIndex match {
         case Left(buffer) =>
           buffer.addOne(stack)
           return
         case Right(index) => index
       }
      case label: FixedLabel =>
        label.offset.getInstructionOffset
    }

    labelStacks.get(index) match {
      case None =>
        labelStacks.addOne(index, stack)
      case Some(expected) =>
        @tailrec
        def check(aa: VStack, bb: VStack): Unit =
          (aa, bb) match {
            case (aa, bb) if aa eq bb =>
            case (a :: ra, b :: rb) =>
              assert(a.afterJoin eq b.afterJoin, s"${a.afterJoin} not equal to ${b.afterJoin}")
              check(ra, rb)
            case _ =>
              throw new AssertionError("incoming stack does not have expected size")
          }

        check(expected, stack)
    }
  }

  def createVariable(descriptor: ScalaDfaVariableDescriptor): DfaVariableValue = factory.getVarFactory.createVariableValue(descriptor)

  def flushFields(): Unit = addInstruction(new FlushFieldsInstruction)

  def finish(element: PsiElement): Unit = addInstruction(new FinishElementInstruction(element))

  def dup(stackValue: StackValue): StackValue = {
    stack.checkTop(stackValue)
    addPushingInstruction(new DupInstruction)
  }

  def push(dfType: DfType, anchor: DfaAnchor = null): StackValue =
    addPushingInstruction(new PushValueInstruction(dfType, anchor))

  def pop(firstValue: StackValue, restValues: StackValue*): Unit =
    pop(firstValue +: restValues)

  def pushVariable(descriptor: ScalaDfaVariableDescriptor, expression: ScExpression): StackValue = {
    val dfaVariable = createVariable(descriptor)
    addPushingInstruction(new JvmPushInstruction(dfaVariable, ScalaStatementAnchor(expression)))
  }

  def pop(values: Seq[StackValue]): Unit = {
    val size = values.size

    stack.pop(values)

    if (size == 1) {
      addInstruction(new PopInstruction)
    } else if (size > 1) {
      addInstruction(new SpliceInstruction(values.size))
    }
  }

  def assign(destination: DfaVariableValue, value: StackValue, anchor: Option[DfaAnchor] = None): Unit =
    addPoppingInstruction(value, new SimpleAssignmentInstruction(anchor.orNull, destination))


  def assign(destination: DfaVariableValue, value: StackValue, anchor: DfaAnchor): Unit =
    assign(destination, value, Some(anchor))

  def convert(value: StackValue, targetType: PsiPrimitiveType, @Nullable anchor: DfaAnchor = null): StackValue = {
    stack.pop(value)
    addPushingInstruction(new PrimitiveConversionInstruction(targetType, anchor))
  }

  def attachAnchor(tos: StackValue, anchor: DfaAnchor): Unit = {
    stack.checkTop(tos)
    addInstruction(new ResultOfInstruction(anchor))
  }

  def invoke(args: Seq[StackValue],
             invocationInfo: InvocationInfo, invocationAnchor: ScalaDfaAnchor,
             qualifier: Option[ScalaDfaVariableDescriptor],
             currentAnalysedMethodInfo: AnalysedMethodInfo): StackValue = {
    stack.pop(args)
    addPushingInstruction(
      new ScalaInvocationInstruction(
        invocationInfo,
        invocationAnchor,
        qualifier,
        exceptionTransfer = maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE),
        currentAnalysedMethodInfo
      )
    )
  }

  def convertPrimitive(value: StackValue, targetType: PsiPrimitiveType, @Nullable anchor: DfaAnchor = null): StackValue = {
    stack.pop(value)
    addPushingInstruction(new PrimitiveConversionInstruction(targetType, anchor))
  }

  def binaryNumOp(leftValue: StackValue,
                  rightValue: StackValue,
                  binOp: LongRangeBinOp,
                  @Nullable anchor: DfaAnchor = null): StackValue = {
    stack.pop(Seq(leftValue, rightValue))
    addPushingInstruction(new NumericBinaryInstruction(binOp, anchor))
  }

  def binaryBoolOp(leftValue: StackValue,
                   rightValue: StackValue,
                   binOp: RelationType,
                   forceEqualityByContent: Boolean,
                   @Nullable anchor: DfaAnchor = null): StackValue = {
    stack.pop(Seq(leftValue, rightValue))
    addPushingInstruction(new BooleanBinaryInstruction(binOp, forceEqualityByContent, anchor))
  }

  def not(value: StackValue, @Nullable anchor: DfaAnchor = null): StackValue = {
    stack.pop(value)
    addPushingInstruction(new NotInstruction(anchor))
  }

  def stackSnapshot: StackSnapshot = new StackSnapshot(stack.current)

  def goto(label: Label): Unit = {
    addIncomingStack(label, stack.current)
    addInstruction(new GotoInstruction(label.offset))
    stack.deactivate()
  }

  def restore(stackSnapshot: StackSnapshot, top: Seq[StackValue] = Seq.empty): Unit =
    stack.restore(top.reverseIterator.toList ::: stackSnapshot.stack)

  def gotoIf(value: StackValue, compareTo: DfType, label: Label, @Nullable anchor: PsiElement): Unit = {
    stack.pop(value)
    addIncomingStack(label, stack.current)
    addInstruction(new ConditionalGotoInstruction(label.offset, compareTo, anchor))
  }

  def gotoIf(value: StackValue, compareTo: DfType, label: Label, anchor: Option[PsiElement] = None): Unit =
    gotoIf(value, compareTo, label, anchor.orNull)

  def newDeferredLabel(): DeferredLabel = new DeferredLabel

  def newLabelHere(): FixedLabel = {
    val label = new FixedLabel(flow.getInstructionCount)
    addIncomingStack(label, stack.current)
    label
  }

  def anchorLabel(label: DeferredLabel): Unit = {
    val index = flow.getInstructionCount
    addIncomingStack(label, stack.current)
    label.offset.setOffset(index)
    val Left(stacks) = label.stacksOrIndex
    label.stacksOrIndex = Right(index)
    stacks.foreach(addIncomingStack(label, _))
  }

  def ret(expression: Option[ScExpression]): Unit =
    addInstruction(new ReturnInstruction(factory, trapTracker.trapStack(), expression.orNull))

  def throws(exceptionType: String, @Nullable anchor: PsiElement): Unit =
    throws(trapTracker.transferValue(exceptionType), anchor)

  def throws(transfer: DfaControlTransferValue, @Nullable anchor: PsiElement): Unit =
    addInstruction(new ThrowInstruction(transfer, anchor))


  def maybeThrow(exceptionType: String = CommonClassNames.JAVA_LANG_THROWABLE): Unit = {
    maybeTransferValue(exceptionType).foreach(throws(_, null))
  }

  def ensure(value: StackValue,
             rel: RelationType,
             compareTo: DfType,
             @Nullable transfer: DfaControlTransferValue = null,
             @Nullable problem: UnsatisfiedConditionProblem = null): Unit = {
    stack.checkTop(value)
    addInstruction(new EnsureInstruction(problem, rel, compareTo, transfer))
  }

  def ensureInBounds(container: StackValue, index: StackValue, exception: String, problem: IndexOutOfBoundsProblem): Unit = {
    stack.pop(index)
    stack.pop(container)
    addInstruction(new EnsureIndexInBoundsInstruction(problem, maybeTransferValue(exception).orNull))
  }

  private def maybeTransferValue(exceptionName: String): Option[DfaControlTransferValue] = Option(trapTracker.maybeTransferValue(exceptionName))

  /*

  def transferValue(transfer: ExceptionTransfer): DfaControlTransferValue = trapTracker.transferValue(transfer)

  def pushTrap(trap: DfaControlTransferValue.Trap): Unit = trapTracker.pushTrap(trap)
   */

  def joinHere(first: StackValue, rest: StackValue*): StackValue = joinHere(first +: rest)
  def joinHere(values: Seq[StackValue]): StackValue = {
    val posOnStack = values.head.posOnStack
    val result = new StackValue(flow.getInstructionCount, posOnStack)
    for (elem <- values.iterator) {
      elem.setJoinedInto(result)
    }
    result
  }

  def build(): ControlFlow = {
    stack.finish()
    flow.finish()
    flow
  }
}

object InstructionBuilder {
  final class StackSnapshot(private[InstructionBuilder] val stack: VStack) extends AnyVal
  private type VStack = List[StackValue]

  private final class StackManager {
    private var active: Boolean = true
    private var stack: VStack = Nil

    def stackHeight: Int = stack.headOption.fold(0)(_.posOnStack + 1)

    def push(idx: Int): StackValue = {
      assert(active)
      val value = new StackValue(idx, stackHeight)
      stack ::= value
      value
    }

    def pop(values: Seq[StackValue]): Unit =
      values.reverseIterator.foreach(pop)

    def pop(value: StackValue): Unit = {
      checkTop(value)
      stack = stack.tail
    }

    def checkTop(value: StackValue): Unit = {
      assert(active)
      assert(!value.hasBeenJoined, s"${value} is joined and cannot be used (joined into: ${value.afterJoin})")
      assert(stack.nonEmpty, s"stack is empty, cannot inspect $value")
      val expected = stack.head.afterJoin

      assert(value eq expected, s"Expected $value, but found $expected on stack")
    }

    def restore(stack: VStack): Unit = {
      assert(!active)
      this.stack = stack
      active = true
    }

    def deactivate(): Unit = {
      assert(active)
      active = false
    }

    def current: VStack = {
      assert(active)
      stack
    }

    def finish(): Unit = {
      assert(active)
      assert(stack.isEmpty, s"Tried to end non-empty stack ($stack)")
    }
  }

  final class StackValue(private[InstructionBuilder] var instructionIndex: Int,
                         private[InstructionBuilder] val posOnStack: Int) {
    private var joinedInto: StackValue = _

    private[InstructionBuilder] def hasBeenJoined: Boolean = joinedInto != null

    private[InstructionBuilder] def setJoinedInto(target: StackValue): Unit = {
      assert(joinedInto == null, s"$this was already joined into ${target}")
      assert(target.posOnStack == posOnStack, s"Cannot join $this into $target, because stack position is different")
      joinedInto = target.afterJoin
    }

    private[InstructionBuilder] def afterJoin: StackValue = {
      if (joinedInto == null) {
        this
      } else {
        joinedInto = joinedInto.afterJoin
        joinedInto
      }
    }

    override def toString: String =
      s"StackValue(#$posOnStack on stack, created in #$instructionIndex)"
  }

  sealed abstract class Label {
    private[InstructionBuilder] def offset: ControlFlowOffset
  }

  final class DeferredLabel extends Label {
    private[InstructionBuilder] var stacksOrIndex: Either[mutable.ArrayBuffer[VStack], Int] = Left(mutable.ArrayBuffer.empty)
    private[InstructionBuilder] override val offset = new DeferredOffset
  }

  final class FixedLabel(index: Int) extends Label {
    private[InstructionBuilder] override val offset = new FixedOffset(index)
  }

  /*private def mergeStack(oldS: VStack, newS: VStack, mergeIndex: Int): VStack = {
    (oldS, newS) match {
      case (a, b) if a eq b => a
      case (Nil, Nil) => Nil
      case (a :: restOld, b :: restNew) =>
        assert(a.posOnStack == b.posOnStack, s"Tried to merge stack elements with different positions ($a, $b")
        val merged =
          if (a eq b) a
          else if (a.instructionIndex == mergeIndex) {
            assert(!a.hasBeenJoined)
            b.setJoinedInto(a)
            a
          } else {
            val result = new StackValue(a.posOnStack, mergeIndex)
            a.setJoinedInto(result)
            b.setJoinedInto(result)
            result
          }
        merged :: mergeStack(restOld, restNew, mergeIndex)
      case _ =>
        throw new AssertionError("Tried to merge stacks with different height")
    }
  }*/
}