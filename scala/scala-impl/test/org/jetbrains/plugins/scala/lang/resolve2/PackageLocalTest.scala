package org.jetbrains.plugins.scala
package lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class PackageLocalTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "element" / "packagelocalclash"

  def testC(): Unit = doTest()
}
