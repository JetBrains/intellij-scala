package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.10.2008
  */
trait ScParamClausesStub extends StubElement[ScParameters]