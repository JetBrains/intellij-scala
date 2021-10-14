package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir

import com.intellij.codeInspection.dataFlow.Mutability
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.types.{DfType, DfTypes}
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfo
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.ir.SpecialSupportUtils.{retrieveListSize, retrieveSingleProperArgumentValue}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.ScalaCollectionImmutable

//noinspection UnstableApiUsage
object CollectionsSpecialSupport {

  def findSpecialSupport(invocationInfo: InvocationInfo, argumentValues: Map[Argument, DfaValue])
                        (implicit factory: DfaValueFactory): Option[MethodEffect] = {
    val typeInfo = for {
      invokedElement <- invocationInfo.invokedElement
      invokedName <- invokedElement.qualifiedName
    } yield invokedName match {
      case s"${ScalaCollectionImmutable}.List.::" => supportConsOperator(invocationInfo, argumentValues)
      case s"${ScalaCollectionImmutable}.List.apply" => supportListFactoryApply(invocationInfo, argumentValues)
      case _ => None
    }

    typeInfo.flatten.map(dfType => MethodEffect(factory.fromDfType(dfType), isPure = true))
  }

  private def supportListFactoryApply(invocationInfo: InvocationInfo,
                                      argumentValues: Map[Argument, DfaValue]): Option[DfType] = {
    retrieveSingleProperArgumentValue(invocationInfo, argumentValues).map(_.getDfType)
  }

  private def supportConsOperator(invocationInfo: InvocationInfo,
                                  argumentValues: Map[Argument, DfaValue]): Option[DfType] = {
    invocationInfo.thisArgument
      .flatMap(argumentValues.get)
      .flatMap(retrieveListSize)
      .map(size => SpecialField.COLLECTION_SIZE.asDfType(DfTypes.intValue(size + 1)))
      .map(_.meet(Mutability.UNMODIFIABLE.asDfType()))
  }
}
