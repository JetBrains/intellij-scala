package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class ParameterizedMethodNameClashTest extends ScalaResolveTestCase {
  override def folderPath: String = super.folderPath + "resolve/functions/"

  def testSCL9892() = {
    val ref: PsiReference = findReferenceAtCaret
    val resolved: PsiElement = ref.resolve
    assert(resolved.isInstanceOf[ScFunctionDefinition])
    assert(resolved.getText == "def foo[T](t1: In[T], t2: In[T]) = println(\"f1\")")
  }
}
