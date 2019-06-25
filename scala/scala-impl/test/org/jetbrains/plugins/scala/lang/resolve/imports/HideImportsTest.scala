package org.jetbrains.plugins.scala
package lang
package resolve
package imports


import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert

class HideImportsTest extends ScalaResolveTestCase {
  override def folderPath: String = super.folderPath + "resolve/imports/simple/"

  def testHidePredefImplicit() {
    findReferenceAtCaret() match {
      case r: ScReference =>
        val results = r.multiResolveScala(false)

        assert(results.length == 1)
        val srr = results(0)
        Assert.assertEquals("caPitalize", srr.element.name)
      case _ => throw new Exception("Wrong reference!")
    }
  }
}