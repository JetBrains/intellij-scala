package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Variance
import org.junit.Assert.assertSame
import org.junit.Test

class UpdateTest {
  @Test
  def simpleUpdateSupportsBothFunctionArities(): Unit = {
    val simple: SimpleUpdate = _ => AfterUpdate.Stop
    val update: Update = simple
    val function: (ScType, Variance) => AfterUpdate = simple

    assertSame(AfterUpdate.Stop, simple(null))
    assertSame(AfterUpdate.Stop, update(null, Variance.Covariant))
    assertSame(AfterUpdate.Stop, function(null, Variance.Contravariant))
  }
}
