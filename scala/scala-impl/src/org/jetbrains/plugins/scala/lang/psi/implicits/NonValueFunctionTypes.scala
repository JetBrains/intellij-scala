package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.functionTypeNoImplicits
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.implicits.NonValueFunctionTypes._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.macroAnnotations.Measure

private case class NonValueFunctionTypes(fun: ScFunction, substitutor: ScSubstitutor, typeFromMacro: Option[ScType]) {

  @volatile
  private var _undefinedData: Option[UndefinedTypeData] = _
  @volatile
  private var _methodTypeData: Option[MethodTypeData] = _

  //lazy vals may lead to deadlock, see SCL-17722
  private def lazyUndefinedData: Option[UndefinedTypeData] = {
    if (_undefinedData == null) {
      _undefinedData = computeUndefinedType(fun, substitutor, typeFromMacro)
    }
    _undefinedData
  }

  private def lazyMethodTypeData: Option[MethodTypeData] = {
    if (_methodTypeData == null) {
      _methodTypeData = lazyUndefinedData.flatMap(computeMethodType(fun, substitutor, _))
    }
    _methodTypeData
  }

  def undefinedType: Option[ScType] = lazyUndefinedData.map(_.undefinedType)

  def hadDependents: Boolean = lazyUndefinedData.exists(_.hadDependent)

  def methodType: Option[ScType] = lazyMethodTypeData.map(_.methodType)

  def hasImplicitClause: Boolean = lazyMethodTypeData.exists(_.hasImplicitClause)
}

private object NonValueFunctionTypes {

  private case class UndefinedTypeData(undefinedType: ScType, hadDependent: Boolean)

  private case class MethodTypeData(methodType: ScType, hasImplicitClause: Boolean)

  @Measure
  private def computeMethodType(fun: ScFunction,
                                substitutor: ScSubstitutor,
                                undefinedTypeData: UndefinedTypeData): Option[MethodTypeData] = {
    val typeParameters = fun.typeParametersWithExtension
    //@TODO: multiple using clauses
    val clauses        = fun.parameterClausesWithExtension
    val implicitClause = clauses.find(_.isImplicitOrUsing)

    if (typeParameters.isEmpty && implicitClause.isEmpty) {
      None
    } else {
      val undefinedReturnType = undefinedTypeData.undefinedType

      val methodOrReturnType = implicitClause match {
        case None => undefinedReturnType
        case Some(clause) =>
          ScMethodType(undefinedReturnType, clause.getSmartParameters, isImplicit = true)(fun.elementScope)
      }

      val polymorphicTypeParameters = typeParameters.map(TypeParameter(_))

      val scType =
        if (polymorphicTypeParameters.isEmpty) methodOrReturnType
        else ScTypePolymorphicType(methodOrReturnType, polymorphicTypeParameters)

      Some(MethodTypeData(substitutor(scType), implicitClause.nonEmpty))
    }
  }

  @Measure
  private def computeUndefinedType(fun: ScFunction,
                                   substitutor: ScSubstitutor,
                                   typeFromMacro: Option[ScType]): Option[UndefinedTypeData] = {
    val ft = functionTypeNoImplicits(fun)

    ft match {
      case Some(_funType: ScType) =>
        val funType            = typeFromMacro.getOrElse(_funType)
        val undefineTypeParams = ScalaPsiUtil.undefineMethodTypeParams(fun)
        val substedFunTp       = substitutor.followed(undefineTypeParams)(funType)
        val withoutDependents  = approximateDependent(substedFunTp, fun.parameters.toSet)
        val undefinedType      = withoutDependents.getOrElse(substedFunTp)

        Option(UndefinedTypeData(undefinedType, withoutDependents.nonEmpty))
      case _ =>
        None
    }
  }

  /**
   * Dependency on an implicit argument is like a dependency on type parameter, thus
   * before checking implicit return type conformance we have to substitute parameter-dependent
   * types with `UndefinedType`, otherwise compatibility check is bound to fail.
   * We also have to verify (after we successfully found some implicit to be compatible)
   * that result type with argument-dependent types restored does indeed conform to `tp`.
   *
   * @param tpe Return type of an implicit currently undergoing a compatibility check
   * @return `tpe` with parameter-dependent types replaced with `UndefinedType`s,
   *         and a mean of reverting this process (useful once type parameters have been inferred
   *         and dependents need to actually be updated according to argument types)
   */
  private def approximateDependent(tpe: ScType, params: Set[ScParameter]): Option[ScType] = {

    var hasDependents = false

    val updated = tpe.updateRecursively {
      case original@ScProjectionType(ScDesignatorType(p: ScParameter), _) if params.contains(p) =>
        hasDependents = true
        UndefinedType(p, original)
    }

    if (hasDependents) Some(updated) else None
  }
}
