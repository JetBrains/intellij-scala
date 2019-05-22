package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.{ScExpressionOwnerStub, ScTypeElementOwnerStub}

/**
  * @author adkozlov
  */
trait ScPropertyStub[P <: ScValueOrVariable] extends StubElement[P]
  with ScTypeElementOwnerStub[P]
  with ScExpressionOwnerStub[P]
  with ScMemberOrLocal
  with ScImplicitInstanceStub {

  def isDeclaration: Boolean

  def isImplicit: Boolean

  def names: Array[String]
}
