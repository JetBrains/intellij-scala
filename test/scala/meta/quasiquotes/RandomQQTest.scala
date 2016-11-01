package scala.meta.quasiquotes

/**
  * @author mutcianm
  * @since 28.10.16.
  */
class RandomQQTest extends QuasiQuoteTypeInferenceTestBase {

  def testPatVarTermApply() = doTest(
    s"""
       |${START}p"Foo"$END
       |//Pat.Var.Term
     """.stripMargin
  )

  def testPatCaseApply() = doTest(
    s"""
       |${START}p"case Foo(x) =>"$END
       |//Case
     """.stripMargin
  )

  def testPatExtractApply() = doTest(
    s"""
       |${START}p"Some(x)"$END
       |//Pat.Extract
     """.stripMargin
  )

  def testPatTypeVarApply() = doTest(
    s"""
       |${START}pt"foo"$END
       |//Pat.Var.Type
     """.stripMargin
  )

  def testPatTypeWildcardApply() = doTest(
    s"""
       |${START}pt"_"$END
       |//Pat.Type.Wildcard
     """.stripMargin
  )

  def testTermArgApply() = doTest(
    s"""
       |${START}arg"a: Int"$END
       |//Term.Ascribe
     """.stripMargin
  )

  def testModAnnotApply() = doTest(
    s"""
       |${START}mod"@foo"$END
       |//Mod.Annot
     """.stripMargin
  )

  def testTypeArgInfixApply() = doTest(
    s"""
       |${START}targ"T ^ U"$END
       |//Type.ApplyInfix
     """.stripMargin
  )

  def testPatArgTypedApply() = doTest(
    s"""
       |${START}parg"a:Int"$END
       |//Pat.Typed
     """.stripMargin
  )

  def testCtorApplyApply() = doTest(
    s"""
       |${START}ctor"A(b)"$END
       |//Term.Apply
     """.stripMargin
  )

  def testCtorRefNameApply() = doTest(
    s"""
       |${START}ctor"A"$END
       |//Ctor.Ref.Name
     """.stripMargin
  )


  def testTermParamApply() = doTest(
    s"""
       |${START}param"a: A"$END
       |//Term.Param
     """.stripMargin
  )

  def testTypeParamApply() = doTest(
    s"""
       |${START}tparam"f <: A with B forSome { val x: Int }"$END
       |//Type.Param
     """.stripMargin
  )

  def testSourceApply() = doTest(
    s"""
       |${START}source"class Foo"$END
       |//Source
     """.stripMargin
  )

  def testImporterApply() = doTest(
    s"""
       |${START}importer"foo.bar"$END
       |//Importer
     """.stripMargin
  )

  def testImporteeApply() = doTest(
    s"""
       |${START}importee"foo"$END
       |//Importee.Name
     """.stripMargin
  )

  def testEnumeratorApply() = doTest(
    s"""
       |${START}enumerator"x <- y"$END
       |//Enumerator.Generator
     """.stripMargin
  )

}
