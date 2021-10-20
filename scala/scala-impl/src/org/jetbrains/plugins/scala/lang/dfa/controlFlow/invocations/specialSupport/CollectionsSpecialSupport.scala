package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir.MethodEffect
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport.SpecialSupportUtils.{collectionSizeFromDfaValueInState, retrieveSingleProperArgumentValue}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.dfTypeImmutableCollection

//noinspection UnstableApiUsage
object CollectionsSpecialSupport {

  def findSpecialSupportForCollections(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                       state: DfaMemoryState)(implicit factory: DfaValueFactory): Option[MethodEffect] = {
    val typeInfo = for {
      invokedElement <- invocationInfo.invokedElement
      invokedName <- invokedElement.qualifiedName
    } yield invokedName match {
      case name if !name.startsWith(ScalaCollection) => (None, false)
      case name if name.endsWith("immutable.List.::") =>
        (supportConsOperator(invocationInfo, argumentValues, state), true)
      case name if name.endsWith("immutable.List.apply") || name.endsWith("IterableFactory.apply") =>
        (supportListFactoryApply(invocationInfo, argumentValues), true)
      case name if name.endsWith("TraversableLike.map") =>
        (supportSequenceMap(invocationInfo, argumentValues, state), false)
      case name if name.startsWith(ScalaCollectionImmutable) => (None, true)
      case _ => (None, false)
    }

    typeInfo match {
      case Some((Some(dfType), isPure)) => Some(MethodEffect(factory.fromDfType(dfType), isPure))
      case Some((None, isPure)) => Some(MethodEffect(factory.fromDfType(DfType.TOP), isPure))
      case _ => None
    }
  }

  private def supportListFactoryApply(invocationInfo: InvocationInfo,
                                      argumentValues: Map[Argument, DfaValue]): Option[DfType] = {
    if (invocationInfo.properArguments.flatten.isEmpty) { // Proper vararg argument is empty
      Some(dfTypeImmutableCollection(0))
    } else retrieveSingleProperArgumentValue(invocationInfo.properArguments, argumentValues).map(_.getDfType)
  }

  private def supportConsOperator(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                  state: DfaMemoryState)(implicit factory: DfaValueFactory): Option[DfType] = {
    val previousSize = retrievePreviousSizeOfCollection(invocationInfo, argumentValues, state, sizeDifference = 0)
    previousSize.map(_ + 1).map(dfTypeImmutableCollection)
  }

  private def supportSequenceMap(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                 state: DfaMemoryState)(implicit factory: DfaValueFactory): Option[DfType] = {
    val previousSize = retrievePreviousSizeOfCollection(invocationInfo, argumentValues, state, sizeDifference = 0)
    previousSize.map(dfTypeImmutableCollection)
  }

  private def retrievePreviousSizeOfCollection(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                               state: DfaMemoryState, sizeDifference: Int)
                                              (implicit factory: DfaValueFactory): Option[Int] = {
    val thisArgDfaValue = invocationInfo.thisArgument.flatMap(argumentValues.get)
    thisArgDfaValue.flatMap(collectionSizeFromDfaValueInState(_, state))
  }
}
