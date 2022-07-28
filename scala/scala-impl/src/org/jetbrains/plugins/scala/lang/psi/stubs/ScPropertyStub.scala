package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.{ScExpressionOwnerStub, ScTypeElementOwnerStub}

trait ScPropertyStub[P <: ScValueOrVariable] extends StubElement[P]
  with ScTopLevelElementStub[P]
  with ScTypeElementOwnerStub[P]
  with ScExpressionOwnerStub[P]
  with ScMemberOrLocal[P]
  with ScImplicitStub[P] {

  def isDeclaration: Boolean

  def isImplicit: Boolean

  def names: Array[String]

  /**
   * Non-trivial class names in property type.
   * It is in the same form as written in source or decompiled class file, so it may have prefix.
   */
  def classNames: Array[String]

  override def implicitClassNames: Array[String] =
    if (isImplicit) classNames else EMPTY_STRING_ARRAY
}
