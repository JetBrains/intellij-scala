package scala.meta.quasiquotes

class RandomQQTest extends QuasiQuoteTypeInferenceTestBase {

  def testPatVarTermApply(): Unit = doTest(
    s"""${START}p"Foo"$END
       |//Pat.Var
       |""".stripMargin
  )

  def testPatCaseApply(): Unit = doTest(
    s"""${START}p"case Foo(x) =>"$END
       |//Case
       |""".stripMargin
  )

  def testPatExtractApply(): Unit = doTest(
    s"""${START}p"Some(x)"$END
       |//Pat.Extract
       |""".stripMargin
  )

  def testPatTypeVarApply(): Unit = doTest(
    s"""${START}t"foo"$END
       |//Type.Name
       |""".stripMargin
  )

  def testPatTypeWildcardApply(): Unit = doTest(
    s"""${START}t"_"$END
       |//Type.Placeholder
       |""".stripMargin
  )

  def testModAnnotApply(): Unit = doTest(
    s"""${START}mod"@foo"$END
       |//Mod.Annot
       |""".stripMargin
  )

  def testTypeArgInfixApply(): Unit = doTest(
    s"""${START}t"T ^ U"$END
       |//Type.ApplyInfix
       |""".stripMargin
  )

  def testPatArgTypedApply(): Unit = doTest(
    s"""${START}p"a:Int"$END
       |//Pat.Typed
       |""".stripMargin
  )

  def testCtorApplyApply(): Unit = doTest(
    s"""${START}q"A(b)"$END
       |//Term.Apply
       |""".stripMargin
  )

  def testCtorRefNameApply(): Unit = doTest(
    s"""${START}q"A"$END
       |//Term.Name
       |""".stripMargin
  )


  def testInit(): Unit = doTest(
    s"""${START}init"A(b)"$END
       |//Init
       |""".stripMargin
  )

  def testSelf(): Unit = doTest(
    s"""${START}self"A"$END
       |//Self
       |""".stripMargin
  )

  def testTermParamApply(): Unit = doTest(
    s"""${START}param"a: A"$END
       |//Term.Param
       |""".stripMargin
  )

  def testTypeParamApply(): Unit = doTest(
    s"""${START}tparam"f <: A with B forSome { val x: Int }"$END
       |//Type.Param
       |""".stripMargin
  )

  def testSourceApply(): Unit = doTest(
    s"""${START}source"class Foo"$END
       |//Source
       |""".stripMargin
  )

  def testImporterApply(): Unit = doTest(
    s"""${START}importer"foo.bar"$END
       |//Importer
       |""".stripMargin
  )

  def testImporteeApply(): Unit = doTest(
    s"""${START}importee"foo"$END
       |//Importee.Name
       |""".stripMargin
  )

  def testEnumeratorApply(): Unit = doTest(
    s"""${START}enumerator"x <- y"$END
       |//Enumerator.Generator
       |""".stripMargin
  )
}
