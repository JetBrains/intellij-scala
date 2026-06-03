package org.jetbrains.plugins.scala
package lang
package transformation
package functions

class ExpandPlaceholderSyntaxTest extends TransformerTest(new ExpandPlaceholderSyntax()) {

  def testUnderscore(): Unit = check(
    before = "_.foo",
    after = "x => x.foo"
  )()

  def testUnderscoreType(): Unit = check(
    before = "(_: A).foo",
    after = "(a: A) => a.foo"
  )()

  def testMultipleUnderscores(): Unit = check(
    before = "_.foo + _.bar",
    after = "(x1, x2) => x1.foo + x2.bar"
  )()

  def testArgument(): Unit = check(
    before = "foo(_)",
    after = "x => foo(x)"
  )()

  def testArgumentType(): Unit = check(
    before = "foo(_: A)",
    after = "(a: A) => foo(a)"
  )()

  def testMultipleArguments(): Unit = check(
    before = "foo(_, _)",
    after = "(x1, x2) => foo(x1, x2)"
  )()

  def testPartialApplication(): Unit = check(
    before = "foo(_, A)",
    after = "x => foo(x, A)"
  )()

  def testUnderscoreAndArgument(): Unit = check(
    before = "_.foo(_)",
    after = "(x1, x2) => x1.foo(x2)"
  )()

  def testExplicitQualifier(): Unit = check(
    before = "a => a.foo",
    after = "a => a.foo"
  )()

  def testExplicitArgument(): Unit = check(
    before = "a => foo(a)",
    after = "a => foo(a)"
  )()

  def testInitializer(): Unit = check(
    before = "val v: A = _",
    after = "val v: A = _"
  )()

  def testEtaExpansion(): Unit = check(
    before = "f _",
    after = "f _"
  )(header = "def f(a: A): C = _")

  // TODO check for name collisions
}
