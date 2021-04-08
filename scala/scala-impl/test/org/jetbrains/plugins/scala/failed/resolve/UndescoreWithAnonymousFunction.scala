package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by kate on 4/1/16.
  */

class UndescoreWithAnonymousFunction extends FailedResolveTest("undescoreWithAnonymous"){
  def testSCL9896(): Unit = doTest()
}
