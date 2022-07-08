package scala.meta.quasiquotes

class StatApplyTest extends QuasiQuoteTypeInferenceTestBase {

  def testClass(): Unit = doTest(
    s"""
       |${START}q"class Foo"$END
       |//Defn.Class
      """.stripMargin
  )

  def testObject(): Unit = doTest(
    s"""
      |${START}q"object Foo"$END
      |//Defn.Object
    """.stripMargin
  )

  def testTrait(): Unit = doTest(
    s"""
      |${START}q"trait Foo"$END
      |//Defn.Trait
    """.stripMargin
  )

  def testDefnDef(): Unit = doTest(
    s"""
       |${START}q"def foo = 42"$END
       |//Defn.Def
     """.stripMargin
  )

  def testDefnVal(): Unit = doTest(
    s"""
       |${START}q"val foo = 42"$END
       |//Defn.Val
     """.stripMargin
  )

}
