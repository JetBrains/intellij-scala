package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.lang.properties.IProperty
import com.intellij.util.PathUtil
import org.junit.Assert

/**
  * Nikolay.Tropin
  * 2014-09-26
  */
class ResolvePropertyKeyTest extends ScalaResolveTestCase {

  override def folderPath: String = {
    val pathname = PathUtil.getJarPathForClass(getClass)
    util.TestUtils.findTestDataDir(pathname) + "/resolve/propertyKey/"
  }

  protected override def rootPath: String = folderPath

  def testMain(): Unit =
    Assert.assertTrue(findReferenceAtCaret.resolve.isInstanceOf[IProperty])
}
