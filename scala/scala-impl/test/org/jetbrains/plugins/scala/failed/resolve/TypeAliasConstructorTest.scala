package org.jetbrains.plugins.scala.failed.resolve

class TypeAliasConstructorTest extends FailableResolveTest("typeAlias") {
  def testSCL13742(): Unit = doTest()
}
