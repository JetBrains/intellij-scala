package org.jetbrains.plugins.scala
package lang.stubs

import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

@Category(Array(classOf[LanguageTests]))
abstract class ScalaStubTestBase extends SimpleTestCase {

  def doTest[Stub <: StubElement[_] : ClassTag](fileText: String)(stubCheck: Stub => Unit): Unit = {
    val psiFile = parseText(fileText)
    val stubTree = psiFile.asInstanceOf[PsiFileImpl].calcStubTree()
    val list = stubTree.getPlainList.asScala
    list.filterByType[Stub].foreach(stubCheck)
  }
}
