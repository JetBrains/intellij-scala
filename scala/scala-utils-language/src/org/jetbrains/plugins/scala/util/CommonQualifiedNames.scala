package org.jetbrains.plugins.scala.util

import com.intellij.psi.CommonClassNames

//noinspection ScalaUnusedSymbol
object CommonQualifiedNames {

  private final val Root = "_root_."

  final val JavaLangObjectFqn = CommonClassNames.JAVA_LANG_OBJECT
  final val JavaLangObjectCanonical = Root + JavaLangObjectFqn

  final val JavaLangComparableFqn = CommonClassNames.JAVA_LANG_COMPARABLE
  final val JavaLangComparableCanonical = Root + JavaLangComparableFqn

  final val JavaIoSerializableFqn = CommonClassNames.JAVA_IO_SERIALIZABLE
  final val JavaIoSerializableCanonical = Root + JavaIoSerializableFqn

  final val ProductFqn = "scala.Product"
  final val ProductCanonical = Root + ProductFqn

  final val ScalaSerializableFqn = "scala.Serializable"
  final val ScalaSerializableCanonical = Root + ScalaSerializableFqn

  def isProductOrScalaSerializableCanonical(canonicalFqn: String): Boolean =
    canonicalFqn == ProductCanonical || canonicalFqn == ScalaSerializableCanonical

  final val AnyRefFqn = "scala.AnyRef"
  final val AnyRefCanonical = Root + AnyRefFqn

  final val BooleanFqn = "scala.Boolean"
  final val BooleanCanonical = Root + BooleanFqn

  final val SeqFqn = "scala.Seq"
  final val SeqCanonical = Root + SeqFqn

  final val AnyFqn = "scala.Any"
  final val AnyCanonical = Root + AnyFqn

  final val NothingFqn = "scala.Nothing"
  final val NothingCanonical = Root + NothingFqn

  final val OptionFqn = "scala.Option"
  final val OptionCanonical = Root + OptionFqn

  final val FunctionFqn = "scala.Function"
  final val FunctionCanonical = Root + FunctionFqn

  //TODO: rename to StringContextFqn
  final val StringContext = "scala.StringContext"
  final val StringContextCanonical = Root + StringContext

  final val ScalaReflectEnumFqn = "scala.reflect.Enum"
  final val ScalaReflectEnumCanonical = Root + ScalaReflectEnumFqn
}
