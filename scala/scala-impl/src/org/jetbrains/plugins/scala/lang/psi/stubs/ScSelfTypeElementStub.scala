package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeElementOwnerStub

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.06.2009
  */
trait ScSelfTypeElementStub extends NamedStub[ScSelfTypeElement] with ScTypeElementOwnerStub[ScSelfTypeElement] {
  def classNames: Array[String]
}