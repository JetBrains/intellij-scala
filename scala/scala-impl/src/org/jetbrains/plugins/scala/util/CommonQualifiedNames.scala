package org.jetbrains.plugins.scala
package util

import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT

object CommonQualifiedNames {

  private final val Root = "_root_."
  private final val Scala = "scala."

  final val JavaObjectFqn = JAVA_LANG_OBJECT
  final val JavaObjectCanonical = Root + JavaObjectFqn

  final val ProductFqn = Scala + "Product"
  final val ProductCanonical = Root + ProductFqn

  final val SerializableFqn = Scala + "Serializable"
  final val SerializableCanonical = Root + SerializableFqn

  final val AnyRefFqn = Scala + "AnyRef"
  final val AnyRefCanonical = Root + AnyRefFqn

  final val BooleanFqn = Scala + "Boolean"
  final val BooleanCanonical = Root + BooleanFqn

  final val SeqFqn = Scala + "Seq"
  final val SeqCanonical = Root + SeqFqn

  final val AnyFqn = Scala + "Any"
  final val AnyCanonical = Root + AnyFqn

  final val NothingFqn = Scala + "Nothing"
  final val NothingCanonical = Root + NothingFqn

  final val OptionFqn = Scala + "Option"
  final val OptionCanonical = Root + OptionFqn

  final val FunctionFqn = Scala + "Function"
  final val FunctionCanonical = Root + FunctionFqn

  final val StringContext = Scala + "StringContext"
  final val StringContextCanonical = Root + StringContext

}
