package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeElementOwnerStub

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */
trait ScAnnotationStub extends StubElement[ScAnnotation] with ScTypeElementOwnerStub[ScAnnotation] {
  def name: Option[String]
}