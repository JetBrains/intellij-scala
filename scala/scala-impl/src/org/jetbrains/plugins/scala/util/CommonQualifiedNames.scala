package org.jetbrains.plugins.scala.util

import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT

object CommonQualifiedNames {

  private final val Root = "_root_."
  final val JavaObjectFqn = JAVA_LANG_OBJECT
  final val JavaObjectCanonical = Root + JavaObjectFqn

  final val ProductFqn = "scala.Product"
  final val ProductCanonical = Root + ProductFqn

  final val SerializableFqn = "scala.Serializable"
  final val SerializableCanonical = Root + SerializableFqn

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

  final val StringContext = "scala.StringContext"
  final val StringContextCanonical = Root + StringContext
}
