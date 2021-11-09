package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.{DfType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory, RelationType}
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.MethodEffect
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport.SpecialSupportUtils.{collectionSizeRangeFromDfaValueInState, collectionSpecificSizeFromDfaValueInState, retrieveSingleProperArgumentValue}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.Packages._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeUtils.{dfTypeImmutableCollectionFromSize, dfTypeImmutableCollectionFromSizeDfType}

//noinspection UnstableApiUsage
object CollectionsSpecialSupport {

  def findSpecialSupportForCollections(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                       state: DfaMemoryState)(implicit factory: DfaValueFactory): Option[MethodEffect] = {
    val typeInfo = for {
      invokedElement <- invocationInfo.invokedElement
      invokedName <- invokedElement.qualifiedName
    } yield invokedName match {
      case name if !name.startsWith(ScalaCollection) => (None, false)
      case name if name.startsWith(ScalaCollectionMutable) => (None, false)
      case name if name.endsWith("immutable.List.::") =>
        (supportConsOperator(invocationInfo, argumentValues, state), true)
      case name if name.endsWith("immutable.List.apply") || name.endsWith("IterableFactory.apply") =>
        (supportListFactoryApply(invocationInfo, argumentValues), true)
      case name if name.endsWith("immutable.List.map") || name.endsWith("TraversableLike.map") =>
        (supportSequenceMap(invocationInfo, argumentValues, state), false)
      case name if name.endsWith("immutable.List.filter") || name.endsWith("TraversableLike.filter") =>
        (supportSequenceFilter(invocationInfo, argumentValues, state), false)
      case name if name.endsWith("SeqOps.size") || name.endsWith("SeqLike.size") =>
        (supportSequenceSize(invocationInfo, argumentValues, state), true)
      case name if name.startsWith(ScalaCollectionImmutable) => (None, true)
      case _ => (None, false)
    }

    typeInfo match {
      case Some((Some(dfType), isPure)) => Some(MethodEffect(factory.fromDfType(dfType), isPure, handledSpecially = true))
      case Some((None, isPure)) => Some(MethodEffect(factory.fromDfType(DfType.TOP), isPure, handledSpecially = isPure))
      case _ => None
    }
  }

  private def supportListFactoryApply(invocationInfo: InvocationInfo,
                                      argumentValues: Map[Argument, DfaValue]): Option[DfType] = {
    if (invocationInfo.properArguments.flatten.isEmpty) { // Proper vararg argument is empty
      Some(dfTypeImmutableCollectionFromSize(0))
    } else retrieveSingleProperArgumentValue(invocationInfo.properArguments, argumentValues).map(_.getDfType)
  }

  private def supportConsOperator(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                  state: DfaMemoryState)(implicit factory: DfaValueFactory): Option[DfType] = {
    val previousSize = retrievePreviousSpecificSizeOfCollection(invocationInfo, argumentValues, state)
    previousSize.map(_ + 1).map(dfTypeImmutableCollectionFromSize)
  }

  private def supportSequenceMap(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                 state: DfaMemoryState)(implicit factory: DfaValueFactory): Option[DfType] = {
    val previousSize = retrievePreviousSpecificSizeOfCollection(invocationInfo, argumentValues, state)
    previousSize.map(dfTypeImmutableCollectionFromSize)
  }

  private def supportSequenceFilter(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                    state: DfaMemoryState)(implicit factory: DfaValueFactory): Option[DfType] = {
    val previousSize = retrievePreviousSpecificSizeOfCollection(invocationInfo, argumentValues, state)
    previousSize.map(DfTypes.intValue)
      .map(_.fromRelation(RelationType.LE))
      .map(_.meet(DfTypes.intValue(0).fromRelation(RelationType.GE)))
      .map(dfTypeImmutableCollectionFromSizeDfType)
  }

  private def supportSequenceSize(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue],
                                  state: DfaMemoryState)(implicit factory: DfaValueFactory): Option[DfType] = {
    val previousSize = retrievePreviousSizeRangeOfCollection(invocationInfo, argumentValues, state)
    previousSize.map(DfTypes.intRange)
  }

  private def retrievePreviousSizeRangeOfCollection(invocationInfo: InvocationInfo,
                                                    argumentValues: Map[Argument, DfaValue],
                                                    state: DfaMemoryState)
                                                   (implicit factory: DfaValueFactory): Option[LongRangeSet] = {
    val thisArgDfaValue = invocationInfo.thisArgument.flatMap(argumentValues.get)
    thisArgDfaValue.flatMap(collectionSizeRangeFromDfaValueInState(_, state))
  }

  private def retrievePreviousSpecificSizeOfCollection(invocationInfo: InvocationInfo,
                                                       argumentValues: Map[Argument, DfaValue],
                                                       state: DfaMemoryState)
                                                      (implicit factory: DfaValueFactory): Option[Int] = {
    val thisArgDfaValue = invocationInfo.thisArgument.flatMap(argumentValues.get)
    thisArgDfaValue.flatMap(collectionSpecificSizeFromDfaValueInState(_, state))
  }
}
