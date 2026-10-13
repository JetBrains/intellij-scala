package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypes

/**
 * Scala constants referencing [[com.intellij.psi.PsiType]] deprecated constants, for use
 * in match expressions.
 */
private[scala] object PsiTypeConstants {
  final val Byte = PsiTypes.byteType()
  final val Char = PsiTypes.charType()
  final val Double = PsiTypes.doubleType()
  final val Float = PsiTypes.floatType()
  final val Int = PsiTypes.intType()
  final val Long = PsiTypes.longType()
  final val Short = PsiTypes.shortType()
  final val Boolean = PsiTypes.booleanType()
  final val Void = PsiTypes.voidType()
  final val Null = PsiTypes.nullType()
}
