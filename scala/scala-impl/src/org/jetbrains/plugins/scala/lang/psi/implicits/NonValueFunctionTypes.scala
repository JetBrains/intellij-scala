package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.caches.measure
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.functionTypeNoImplicits
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.implicits.NonValueFunctionTypes._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{Compatibility, ScType}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider.fromScMethodLike

private case class NonValueFunctionTypes(
  fun:                 ScFunction,
  substitutor:         ScSubstitutor,
  exportedInExtension: Option[ScExtension],
  typeFromMacro:       Option[ScType]
) {

  @volatile
  private var _undefinedData: Option[UndefinedReturnTypeData] = _
  @volatile
  private var _methodTypeData: Option[MethodTypeData] = _

  //lazy vals may lead to deadlock, see SCL-17722
  private def lazyUndefinedData: Option[UndefinedReturnTypeData] = {
    if (_undefinedData == null) {
      _undefinedData = computeUndefinedType(fun, substitutor, exportedInExtension, typeFromMacro)
    }
    _undefinedData
  }

  private def lazyMethodTypeData: Option[MethodTypeData] = {
    if (_methodTypeData == null) {
      _methodTypeData = computeMethodType(fun, substitutor, exportedInExtension)
    }
    _methodTypeData
  }

  def undefinedType: Option[ScType] = lazyUndefinedData.map(_.undefinedType)

  def hadDependents: Boolean = lazyUndefinedData.exists(_.hadDependent)

  def methodType: Option[ScType] = lazyMethodTypeData.map(_.methodType)

  def hasImplicitClause: Boolean         = hasLeadingImplicitClause || hasTrailingImplicitClause
  def hasLeadingImplicitClause: Boolean  = lazyMethodTypeData.exists(_.hasLeadingImplicitClause)
  def hasTrailingImplicitClause: Boolean = lazyMethodTypeData.exists(_.hasTrailingImplicitClause)
}

private object NonValueFunctionTypes {

  private case class UndefinedReturnTypeData(undefinedType: ScType, hadDependent: Boolean)

  private case class MethodTypeData(
    methodType:                ScType,
    hasLeadingImplicitClause:  Boolean,
    hasTrailingImplicitClause: Boolean
  )

  private def computeMethodType(
    fun:                 ScFunction,
    substitutor:         ScSubstitutor,
    exportedInExtension: Option[ScExtension]
  ): Option[MethodTypeData] = measure("NonValueFunctionTypes.computeMethodType") {
    def hasImplicitClause(
      tpe:       ScType,
      isLeading: Boolean
    ): (Boolean, Boolean) = tpe match {
      case ScTypePolymorphicType(inner, _)    => hasImplicitClause(inner, isLeading)
      case ScMethodType(inner, _, isImplicit) =>
        if (isImplicit) {
          if (isLeading) (isLeading, hasImplicitClause(inner, isLeading)._2)
          else           (false, true)
        } else hasImplicitClause(inner, isLeading = false)
      case _ => (false, false)
    }

    val polyOrMethodType = fun.polymorphicType(
      s              = substitutor,
      extensionOwner = exportedInExtension
    )

    val hasTypeParams = polyOrMethodType.is[ScTypePolymorphicType]

    val (hasLeadingImplicits, hasTrailingImplicits) =
      hasImplicitClause(polyOrMethodType, isLeading = true)

    val hasImplicits = hasLeadingImplicits || hasTrailingImplicits

    Option.when(hasTypeParams || hasImplicits)(
      MethodTypeData(
        polyOrMethodType,
        hasLeadingImplicitClause  = hasLeadingImplicits,
        hasTrailingImplicitClause = hasTrailingImplicits
      )
    )
  }

  private def computeUndefinedType(
    fun:                 ScFunction,
    substitutor:         ScSubstitutor,
    exportedInExtension: Option[ScExtension],
    typeFromMacro:       Option[ScType]
  ): Option[UndefinedReturnTypeData] = measure("NonValueFunctionTypes.computeUndefinedType") {
    val ft = functionTypeNoImplicits(fun, exportedInExtension)

    ft match {
      case Some(_funType: ScType) =>
        val funType            = typeFromMacro.getOrElse(_funType)
        val undefineTypeParams = ScalaPsiUtil.undefineMethodTypeParams(fun, exportedInExtension)
        val substedFunTp       = substitutor.followed(undefineTypeParams)(funType)
        val withoutDependents  = Compatibility.approximateDependent(substedFunTp, fun.parameters.toSet)
        val undefinedType      = withoutDependents.getOrElse(substedFunTp)

        Option(UndefinedReturnTypeData(undefinedType, withoutDependents.nonEmpty))
      case _ =>
        None
    }
  }


}
