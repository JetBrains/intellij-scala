package scala.meta.quasiquotes

/**
  * @author mutcianm
  * @since 28.10.16.
  */
class StatUnapplyTest extends QuasiQuoteTypeInferenceTestBase {
  def testTraitUnapply() = doTest(
    s"""
       |val q"trait $$tname[..$$tparams] { ..$$stats }" = q"trait Foo[A,B] { val x = 42 }"
       |$START(tname, tparams, stats)$END
       |//(Type.Name, Seq[Type.Param], Seq[Stat])
     """.stripMargin
  )
}
