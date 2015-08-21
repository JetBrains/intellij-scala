package org.jetbrains.plugins.scala.meta.converter

import org.jetbrains.plugins.scala.meta.TreeConverterTestBaseNoLibrary

import scala.meta.internal.ast._

class TreeConverterExprTest extends TreeConverterTestBaseNoLibrary {

  def testIf() {
    doTest(
      "if (true) 42",
      Term.If(Lit.Bool(value = true), Lit.Int(42), Lit.Unit())
    )
  }
  
  def testIfElse() {
    doTest(
      "if (false) 42 else 0",
      Term.If(Lit.Bool(value = false), Lit.Int(42), Lit.Int(0))
    )
  }
  
  def testIfElseIfElse() {
    doTest(
      "if (true) 42 else if (false) 999 else 0",
      Term.If(Lit.Bool(value = true), Lit.Int(42), Term.If(Lit.Bool(value = false), Lit.Int(999), Lit.Int(0)))
    )
  }

  def testObjectMethodCall() {
    doTest(
      """object Foo { def f() = 42 }
        |//start
        |Foo.f()
      """.stripMargin,
      Term.Apply(Term.Select(Term.Name("Foo"), Term.Name("f")), Nil)
    )
  }
  
  def testNewNoParen() {
    doTest(
      """class Foo
        |//start
        |new Foo
      """.stripMargin,
      Term.New(Template(Nil, List(Ctor.Ref.Name("Foo")), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testNewEmptyParen() {
    doTest(
      """class Foo
        |//start
        |new Foo()
      """.stripMargin,
      Term.New(Template(Nil, List(Term.Apply(Ctor.Ref.Name("Foo"), Nil)), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }
  
  def testNewWithArg() {
    doTest(
      """class Foo(a: Int)
        |//start
        |new Foo(42)
      """.stripMargin,
      Term.New(Template(Nil, List(Term.Apply(Ctor.Ref.Name("Foo"), List(Lit.Int(42)))), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

  def testNewMutiParen() {
    doTest(
      """class Foo(a:Int)(b:String)
        |//start
        |new Foo(42)("")""".stripMargin,
      Term.New(Template(Nil, List(Term.Apply(Term.Apply(Ctor.Ref.Name("Foo"), List(Lit.Int(42))), List(Lit.String("")))), Term.Param(Nil, Name.Anonymous(), None, None), None))
    )
  }

}
