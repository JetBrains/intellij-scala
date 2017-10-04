package org.jetbrains.plugins.scala
package lang
package transformation

import com.intellij.psi.PsiElement

/**
  * @author Pavel Fatin
  */
class CompoundTransformationTest extends TransformationTest {

  override protected def transform(element: PsiElement): Unit =
    Transformer.transform(element)

  // TODO
  def testCompound(): Unit = {} // check(
  //    before =
  //      s"""
  //      println(s"Hello $${for(c <- "world") yield c.toUpper}", "!")
  //    """,
  //    after =
  //      """
  //      Predef.println(Tuple2.apply(StringContext.apply("Hello ", "").s(Predef.augmentString("world").map((c: Char) => Predef.charWrapper(c).toUpper)(Predef.StringCanBuildFrom)), "!"));
  //    """
  //  )()
}
