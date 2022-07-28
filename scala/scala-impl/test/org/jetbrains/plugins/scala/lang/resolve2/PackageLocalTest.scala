package org.jetbrains.plugins.scala
package lang.resolve2

class PackageLocalTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "element/packagelocalclash/"
  }

  def testC(): Unit = doTest()
}
