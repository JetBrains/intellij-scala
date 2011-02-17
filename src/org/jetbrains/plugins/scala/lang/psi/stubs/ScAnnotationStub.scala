package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.expr.ScAnnotation
import com.intellij.psi.stubs.StubElement
import api.base.types.ScTypeElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

trait ScAnnotationStub extends StubElement[ScAnnotation] {
  def getName: String

  def getTypeElement: ScTypeElement

  def getTypeText: String
}