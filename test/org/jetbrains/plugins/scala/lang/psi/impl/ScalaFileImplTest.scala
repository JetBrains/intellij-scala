package org.jetbrains.plugins.scala
package lang
package psi
package impl

import org.junit.Assert
import org.jetbrains.plugins.scala.base.SimpleTestCase

/**
 * Pavel Fatin
 */

class ScalaFileImplTest extends SimpleTestCase {
  def testStripPackages() {
    assertPackagesStrippedAs("", "")
    assertPackagesStrippedAs("package a", "")
    assertPackagesStrippedAs("package a\npackage b", "")

    assertPackagesStrippedAs("package a\nclass C", "class C")
    assertPackagesStrippedAs("package a\npackage b\nclass C", "class C")

    assertPackagesStrippedAs("package a {\nclass A\npackage b {\nclass B\n}\n}", "class A\nclass B\n")
    assertPackagesStrippedAs("package a {\npackage b {\nclass A\n}\npackage c {\nclass B\n}\n}", "class A\nclass B\n")
  }

  def testPathIn() {
    assertPathIs("", List())
    assertPathIs("package a", List(List("a")))
    assertPathIs("package a.b", List(List("a", "b")))
    assertPathIs("package a\npackage b", List(List("a"), List("b")))
    assertPathIs("package a.b\npackage c.d", List(List("a", "b"), List("c", "d")))

    assertPathIs("/* foo */\npackage a", List(List("a")))
    assertPathIs("/* foo */\npackage a\n/* bar */\npackage b", List(List("a"), List("b")))
  }

  def testSetPath() {
    assertPathAddedAs("", List(List("a")), "package a");
    assertPathAddedAs("", List(List("a", "b")), "package a.b");
    assertPathAddedAs("", List(List("a"), List("b")), "package a\npackage b");
    assertPathAddedAs("", List(List("a", "b"), List("c", "d")), "package a.b\npackage c.d");

    assertPathAddedAs("class C", List(List("a")), "package a\n\nclass C");
    assertPathAddedAs("class C", List(List("a"), List("b")), "package a\npackage b\n\nclass C");
  }

  def testSplitsIn() {
    assertSplitsAre(List(), List())
    assertSplitsAre(List(List("a")), List())
    assertSplitsAre(List(List("a", "b")), List())
    assertSplitsAre(List(List("a", "b", "c")), List())
    assertSplitsAre(List(List("a"), List("b")), List(List("a")))
    assertSplitsAre(List(List("a"), List("b"), List("c")), List(List("a"), List("a", "b")))
    assertSplitsAre(List(List("a", "b"), List("c", "d")), List(List("a", "b")))
    assertSplitsAre(List(List("a", "b"), List("c", "d"), List("e")), List(List("a", "b"), List("a", "b", "c", "d")))
  }

  def testSplitAt() {
    assertSplitAs(List(), List(), List())
    assertSplitAs(List(), List("a"), List())
    assertSplitAs(List(List("a")), List(), List(List("a")))
    assertSplitAs(List(List("a")), List("a"), List(List("a")))
    assertSplitAs(List(List("a", "b")), List(), List(List("a", "b")))
    assertSplitAs(List(List("a", "b")), List("c"), List(List("a", "b")))
    assertSplitAs(List(List("a", "b")), List("b"), List(List("a", "b")))
    assertSplitAs(List(List("a", "b")), List("a"), List(List("a"), List("b")))
    assertSplitAs(List(List("a"), List("b")), List(), List(List("a"), List("b")))
    assertSplitAs(List(List("a"), List("b")), List("a"), List(List("a"), List("b")))
    assertSplitAs(List(List("a", "b"), List("c")), List("a"), List(List("a"), List("b"), List("c")))
    assertSplitAs(List(List("a"), List("b", "c")), List("b"), List(List("a"), List("b", "c")))
    assertSplitAs(List(List("a"), List("b", "c")), List("a", "b"), List(List("a"), List("b"), List("c")))
  }

  def testSetPackageName() {
    assertPackageNameSetAs("class C", ("", ""), "class C");
    assertPackageNameSetAs("package foo\n\nclass C", ("", ""), "class C");
    assertPackageNameSetAs("package foo\n\nclass C", ("", "foo.bar"), "package foo.bar\n\nclass C");
    assertPackageNameSetAs("package foo\npackage bar\n\nclass C", ("", "foo.moo"),
          "package foo\npackage moo\n\nclass C");
    assertPackageNameSetAs("package foo\npackage bar\npackage moo\n\nclass C", ("", "foo.bar.moo"),
          "package foo\npackage bar\npackage moo\n\nclass C");
    assertPackageNameSetAs("class C", ("base", "base.foo.bar"), "package base\npackage foo.bar\n\nclass C");
    assertPackageNameSetAs("package foo\n\nclass C", ("foo.bar", "foo.bar.moo"), "package foo.bar\npackage moo\n\nclass C");
    assertPackageNameSetAs("package foo.bar\npackage moo\npackage goo\n\nclass C", ("foo", "foo.bar.moo.goo"),
      "package foo\npackage bar\npackage moo\npackage goo\n\nclass C");
  }

  private def assertPackagesStrippedAs(before: String, after: String) {
    val file = parseText(before)
    ScalaFileImpl.stripPackagesIn(file)
    Assert.assertEquals(describe(parseText(after)), describe(file))
  }

  private def assertPathIs(code: String, path: List[List[String]]) {
    Assert.assertEquals(path, ScalaFileImpl.pathIn(parseText(code)))
  }

  private def assertPathAddedAs(before: String, path: List[List[String]], after: String) {
    val file = parseText(before)
    ScalaFileImpl.addPathTo(file, path)
    Assert.assertEquals(describe(parseText(after)), describe(file))
  }

  private def assertSplitAs(before: List[List[String]], vector: List[String], after: List[List[String]]) {
    Assert.assertEquals(after, ScalaFileImpl.splitAt(before, vector))
  }

  private def assertSplitsAre(path: List[List[String]], vectors: List[List[String]]) {
    Assert.assertEquals(vectors, ScalaFileImpl.splitsIn(path))
  }

  private def assertPackageNameSetAs(before: String, name: (String,  String), after: String ) {
    val file = parseText(before).asInstanceOf[ScalaFileImpl]
    file.setPackageName(name._1, name._2)
    Assert.assertEquals(describe(parseText(after)), describe(file))
  }
}