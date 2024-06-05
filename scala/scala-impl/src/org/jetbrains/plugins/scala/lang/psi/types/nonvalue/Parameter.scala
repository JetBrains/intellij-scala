package org.jetbrains.plugins.scala.lang.psi.types.nonvalue

import com.intellij.psi.PsiParameter
import org.jetbrains.plugins.scala.extensions.PsiParameterExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * Generalized parameter. It's not psi element. So can be used in any place.
  * Some difference
  */
case class Parameter(
  name:           String,
  deprecatedName: Option[String],
  paramType:      ScType,
  expectedType:   ScType, //@TODO: remove ???
  isDefault:      Boolean = false,
  isRepeated:     Boolean = false,
  isByName:       Boolean = false,
  index:          Int = -1,
  psiParam:       Option[PsiParameter] = None,
  defaultType:    Option[ScType] = None
) {
  def paramInCode: Option[ScParameter] = psiParam.collect {
    case parameter: ScParameter => parameter
  }

  def nameInCode: Option[String] = psiParam.map(_.getName)

  def isImplicitOrContextParameter: Boolean = paramInCode.exists(_.isImplicitOrContextParameter)
}

object Parameter {
  def apply(paramType: ScType,
            isRepeated: Boolean,
            isByName: Boolean,
            index: Int,
            name: String): Parameter =
    new Parameter(
      name,
      deprecatedName = None,
      paramType = paramType,
      expectedType = paramType,
      isDefault = false,
      isRepeated = isRepeated,
      isByName = isByName,
      index = index
    )

  def apply(paramType: ScType,
            isRepeated: Boolean,
            index: Int,
            name: String): Parameter =
    new Parameter(
      name,
      deprecatedName = None,
      paramType = paramType,
      expectedType = paramType,
      isDefault = false,
      isRepeated = isRepeated,
      isByName = false,
      index = index
    )

  def apply(paramType: ScType,
            isRepeated: Boolean,
            index: Int): Parameter =
    apply(paramType, isRepeated, index, "")

  def apply(paramType: ScType,
            isRepeated: Boolean,
            isByName: Boolean,
            index: Int): Parameter =
    apply(paramType, isRepeated, isByName, index, "")

  def apply(parameter: PsiParameter): Parameter = parameter match {
    case scParameter: ScParameter =>
      val `type` = scParameter.`type`().getOrNothing

      new Parameter(
        name = scParameter.name,
        deprecatedName = scParameter.deprecatedName,
        paramType = `type`,
        expectedType = `type`,
        isDefault = scParameter.isDefaultParam,
        isRepeated = scParameter.isRepeatedParameter,
        isByName = scParameter.isCallByNameParameter,
        index = scParameter.index,
        psiParam = Some(scParameter),
        defaultType = scParameter.getDefaultExpression.flatMap(_.`type`().toOption)
      )
    case _ =>
      val `type` = parameter.paramType(extractVarargComponent = false)
      fromJavaParameterWithType(parameter, `type`)
  }

  def fromJavaParameterWithType(parameter: PsiParameter, scType: ScType): Parameter = {
    new Parameter(
      name = parameter.getName,
      deprecatedName = None,
      paramType = scType,
      expectedType = scType,
      isDefault = false,
      isRepeated = parameter.isVarArgs,
      isByName = false,
      index = parameter.index,
      psiParam = Some(parameter)
    )
  }
}
