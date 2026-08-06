package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.lang.properties.IProperty
import com.intellij.util.PathUtil
import org.junit.Assert

import java.nio.file.Path

class ResolvePropertyKeyTest extends ScalaResolveTestCase {

  override def folderPath: Path = {
    val pathname = PathUtil.getJarPathForClass(getClass)
    Path.of(util.TestUtils.findTestDataDir(pathname), "resolve", "propertyKey")
  }

  protected override def sourceRootPath: Path = folderPath

  private def doTest(): Unit = {
    val reference = findReferenceAtCaret()
    val resolved = reference.resolve()
    Assert.assertTrue(resolved.isInstanceOf[IProperty])
  }

  def testMain(): Unit = doTest()

  def testInterpolated(): Unit = doTest()
}
