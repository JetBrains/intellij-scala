package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase

/**
  * @author Nikolay.Tropin
  */
abstract class FailedResolveTest(dirName: String) extends ScalaResolveTestCase {

  override def folderPath(): String = s"${super.folderPath()}resolve/failed/$dirName"

  override def rootPath(): String = folderPath()
}
