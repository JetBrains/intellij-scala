package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.psi.PsiClass
import java.lang.String
import psi.api.statements.params.ScParameter
import psi.api.toplevel.typedef.ScTrait
import resolve.ScalaResolveTestCase
import util.TestUtils
import _root_.junit.framework.Assert._

/**
 * @author ilyas
 */

class TypeInferenceTest2 extends ScalaResolveTestCase {

  override protected def getTestDataPath: String = TestUtils.getTestDataPath() + "/typeInference/"

  def testFunParameter {
    val ref = configureByFile("expected/param/Param.scala")
    val resolved = ref.resolve
    assertTrue(resolved.isInstanceOf[ScParameter])
    val string = resolved.asInstanceOf[ScParameter].calcType.toString
    assertFalse(string == "Nothing")
  }

}