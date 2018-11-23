package org.jetbrains.plugins.scala.util

object CommonQualifiedNames {
  private final val rootScala = "_root_.scala."

  final val JavaObject   = "_root_.java.lang.Object"

  final val ProductFqn      = rootScala + "Product"
  final val SerializableFqn = rootScala + "Serializable"
  final val AnyRefFqn       = rootScala + "AnyRef"
  final val BooleanFqn      = rootScala + "Boolean"
  final val SeqFqn          = rootScala + "Seq"
  final val AnyFqn          = rootScala + "Any"
  final val NothingFqn      = rootScala + "Nothing"
  final val OptionFqn       = rootScala + "Option"

}
