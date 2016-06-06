package org.jetbrains.plugins.scala.lang.transformation
package functions

/**
  * @author Pavel Fatin
  */
class ExpandPlaceholderSyntaxTest extends TransformerTest(ExpandPlaceholderSyntax) {
  def testUnderscore() = check(
    "_.foo",
    "x => x.foo"
  )

  def testUnderscoreType() = check(
    "(_: A).foo",
    "(x: A) => x.foo"
  )

  def testMultipleUnderscores() = check(
    "_.foo + _.bar",
    "(x1, x2) => x1.foo + x2.bar"
  )

  def testArgument() = check(
    "foo(_)",
    "x => foo(x)"
  )

  def testArgumentType() = check(
    "foo(_: A)",
    "(x: A) => foo(x)"
  )

  def testMultipleArguments() = check(
    "foo(_, _)",
    "(x1, x2) => foo(x1, x2)"
  )

  def testPartialApplicatoin() = check(
    "foo(_, A)",
    "x => foo(x, A)"
  )

  def testUnderscoreAndArgument() = check(
    "_.foo(_)",
    "(x1, x2) => x1.foo(x2)"
  )

  def testExplicitQualifier() = check(
    "a => a.foo",
    "a => a.foo"
  )

  def testExplicitArgument() = check(
    "a => foo(a)",
    "a => foo(a)"
  )

  def testInitializer() = check(
    "val v: A = _",
    "val v: A = _"
  )

  // TODO generate names based on types
  // TODO check for name collisions
}
