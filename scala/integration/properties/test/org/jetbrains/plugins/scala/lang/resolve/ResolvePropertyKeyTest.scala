package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.lang.properties.IProperty
import com.intellij.util.PathUtil
import org.junit.Assert

class ResolvePropertyKeyTest extends ScalaResolveTestCase {

  override def folderPath: String = {
    val pathname = PathUtil.getJarPathForClass(getClass)
    util.TestUtils.findTestDataDir(pathname) + "/resolve/propertyKey/"
  }

  protected override def sourceRootPath: String = folderPath

  private def doTest(): Unit = {
    val reference = findReferenceAtCaret()
    val resolved = reference.resolve()
    Assert.assertTrue(resolved.isInstanceOf[IProperty])
  }

  def testMain(): Unit = doTest()

  def testInterpolated(): Unit = doTest()
}
