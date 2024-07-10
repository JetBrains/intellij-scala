package org.jetbrains.plugins.scala.testingSupport.munit

trait MUnit_0_7 { self: MUnitTestCase =>
  override def munitVersion: String = "0.7.29"
}

trait MUnit_1_0 { self: MUnitTestCase =>
  override def munitVersion: String = "1.0.0"
}
