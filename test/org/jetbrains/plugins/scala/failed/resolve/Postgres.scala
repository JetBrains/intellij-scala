package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by kate on 4/7/16.
  */

//lots of self type in library, maybe this is cause of problem
class Postgres extends FailedResolveTest("postgresql") {
  override protected def additionalLibraries(): Array[String] = Array("postgresql")

  def testSCL8556(): Unit = doTest()
}
