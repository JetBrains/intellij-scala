package org.jetbrains.plugins.scala
package lang.resolve2

/**
 * @author Alefas
 * @since 26.08.13
 */
class PackageLocalTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "element/packagelocalclash/"
  }

  def testC(): Unit = doTest()
}
