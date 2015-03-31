package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._

class TreeConverterTemplateTest extends TreeConverterTestBase {

  def testTraitEmpty() {
    doTest(
      "trait A",
      Defn.Trait(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil), 
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testTraitEmptyNone() {
    doTest(
      "trait T {}",
      Defn.Trait(Nil, Type.Name("T"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), Some(Nil)))
    )
  }

  def testTraitTyped() {
    doTest(
      "trait T[A]",
      Defn.Trait(Nil, Type.Name("T"), List(Type.Param(Nil, Type.Name("A"), Nil, Type.Bounds(None, None), Nil, Nil)),
        Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil), Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testExtends() {
    doTest(
    """
      |trait B
      |//start
      |trait A extends B
    """.stripMargin,
      Defn.Trait(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, List(Ctor.Ref.Name("B")), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testExtendsEarly() {
    doTest(
      """
        |trait B
        |//start
        |trait A extends { val x: Int = 42 } with B
      """.stripMargin,
       Defn.Trait(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
         Template(List(Defn.Val(Nil, List(Pat.Var.Term(Term.Name("x"))), Some(Type.Name("Int")), Lit.Int(42))),
           List(Ctor.Ref.Name("B")), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }
  
  def testSelf() {
    doTest(
      """
        |trait B
        |//start
        |trait A { self: B => }
      """.stripMargin,
      Defn.Trait(Nil, Type.Name("A"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, Nil, Term.Param(Nil, Term.Name("self"), Some(Type.Name("B")), None), Some(Nil)))
    )
  }

  def testTemplateBody() {
    doTest(
      "trait T { def x: Int }",
      Defn.Trait(Nil, Type.Name("T"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None),
          Some(List(Decl.Def(Nil, Term.Name("x"), Nil, Nil, Type.Name("Int"))))))
    )
  }
  
  def testTemplateExprs() {
    doTest(
      "trait T { def f = 1 ; f()}",
      Defn.Trait(Nil, Type.Name("T"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil), Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), Some(List(Defn.Def(Nil, Term.Name("f"), Nil, Nil, None, Lit.Int(1)), Term.Apply(Term.Name("f"), Nil)))))
    )
  }
  
  def testClassEmpty() {
    doTest(
      "class C",
      Defn.Class(Nil, Type.Name("C"), Nil, Ctor.Primary(Nil, Ctor.Ref.Name("this"), Nil),
        Template(Nil, Nil, Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

}
