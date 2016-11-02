package scala.meta.quasiquotes

/**
  * @author mutcianm
  * @since 21.10.16.
  */
class StatApplyTest extends QuasiQuoteTypeInferenceTestBase {

  def testClass() = doTest(
    s"""
       |${START}q"class Foo"$END
       |//Defn.Class
      """.stripMargin
  )

  def testObject() = doTest(
    s"""
      |${START}q"object Foo"$END
      |//Defn.Object
    """.stripMargin
  )

  def testTrait() = doTest(
    s"""
      |${START}q"trait Foo"$END
      |//Defn.Trait
    """.stripMargin
  )

  def testDefnDef() = doTest(
    s"""
       |${START}q"def foo = 42"$END
       |//Defn.Def
     """.stripMargin
  )

  def testDefnVal() = doTest(
    s"""
       |${START}q"val foo = 42"$END
       |//Defn.Val
     """.stripMargin
  )

}
