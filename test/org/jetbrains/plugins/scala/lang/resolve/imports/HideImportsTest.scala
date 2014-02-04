package org.jetbrains.plugins.scala
package lang
package resolve
package imports


import com.intellij.psi.PsiPolyVariantReference
import java.lang.String
import util.TestUtils
import junit.framework.Assert

class HideImportsTest extends ScalaResolveTestCase {
  override def folderPath: String = super.folderPath() + "resolve/imports/simple/"

  def testHidePredefImplicit() {
    findReferenceAtCaret() match {
      case r: PsiPolyVariantReference => {
        val results = r.multiResolve(false)

        assert(results.length == 1)
        val res = results(0)
        assert(res.isInstanceOf[ScalaResolveResult])
        val srr = res.asInstanceOf[ScalaResolveResult]
        Assert.assertEquals("def caPitalize: Int", srr.element.getText)
      }
      case _ => throw new Exception("Wrong reference!")
    }
  }
}