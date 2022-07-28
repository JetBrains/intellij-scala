package org.jetbrains.plugins.scala.failed.resolve

class UndescoreWithAnonymousFunction extends FailableResolveTest("undescoreWithAnonymous"){
  def testSCL9896(): Unit = doTest()
}
