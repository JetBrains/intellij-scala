package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType

private[scala] object TypeParameterDebugRendering {
  final val RegistryKey = "scala.type.presentation.debug.type.param.id"

  def withTypeParamId(baseText: String, typeParamId: Long): String =
    if (Registry.is(RegistryKey)) s"$baseText#$typeParamId"
    else                          baseText

  def renderNamedTypeName(namedType: NamedType): String = namedType match {
    case tpt: TypeParameterType => withTypeParamId(tpt.name, tpt.typeParamId)
    case _                      => namedType.name
  }
}
