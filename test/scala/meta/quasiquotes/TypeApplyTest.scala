package scala.meta.quasiquotes

/**
  * @author mutcianm
  * @since 24.10.16.
  */
class TypeApplyTest extends QuasiQuoteTypeInferenceTestBase {

  def testStdNames() = doTest(
    s"""
      |${START}t"Int"$END
      |//Type.Name
    """.stripMargin
  )

  def testTypeApply() = doTest(
    s"""
       |${START}t"X[Y,Z]"$END
       |//Type.Apply
     """.stripMargin
  )

  def testTypeSelect() = doTest(
    s"""
      |${START}t"X.Y"$END
      |//Type.Select
    """.stripMargin
  )

  def testTypeProject() = doTest(
    s"""
       |${START}t"X#Y"$END
       |//Type.Project
     """.stripMargin
  )

  def testTypeSingleton() = doTest(
    s"""
       |${START}t"X.type"$END
       |//Type.Singleton
     """.stripMargin
  )

  def testTypeApplyInfix() = doTest(
    s"""
       |${START}t"X Y Z"$END
       |//Type.ApplyInfix
     """.stripMargin
  )

  def testFunctionType() = doTest(
    s"""
       |val atpes: List[Type.Arg] = List(t"X", t"Y")
       |val tpe = t"Z"
       |${START}t"(..$$atpes) => $$tpe"$END
       |//Type.Function
     """.stripMargin
  )

  def testTupleType() = doTest(
    s"""
       |val tpes = List(t"X", t"Y")
       |${START}t"(..$$tpes)"$END
       |//Type.Tuple
     """.stripMargin
  )

  def testExistentialType() = doTest(
    s"""
       |${START}t"X forSome { val a: A }"$END
       |//Type.Existential
     """.stripMargin
  )

  def testTypePlaceholder() = doTest(
    s"""
       |${START}t"_ >: X <: Y"$END
       |//Type.Placeholder
     """.stripMargin
  )

  def testTypeArgByName() = doTest(
    s"""
       |${START}targ"=> X"$END
       |//Type.Arg.ByName
     """.stripMargin
  )

  def testTypeArgRepeated() = doTest(
    s"""
       |${START}targ"X*"$END
       |//Type.Arg.Repeated
     """.stripMargin
  )

}
