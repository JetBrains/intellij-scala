package org.jetbrains.plugins.scala.lang.transformation

/**
  * @author Pavel Fatin
  */
class CompoundTransforationTest extends TransformationTest(Transformer.transform(_)) {
  def testCompound() = {} //todo:

  /*check(
    s"""
      println(s"Hello $${for(c <- "world") yield c.toUpper}", "!")
    """,
    """
      Predef.println(Tuple2.apply(StringContext.apply("Hello ", "").s(Predef.augmentString("world").map((c: Char) => Predef.charWrapper(c).toUpper)(Predef.StringCanBuildFrom)), "!"));
    """
  )*/
}
