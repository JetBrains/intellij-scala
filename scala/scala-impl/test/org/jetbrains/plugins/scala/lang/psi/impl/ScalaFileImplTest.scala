package org.jetbrains.plugins.scala
package lang
package psi
package impl

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.junit.Assert

class ScalaFileImplTest extends SimpleTestCase {
//  def testStripPackages() {
//    assertPackagesStrippedAs("", "")
//    assertPackagesStrippedAs("package a", "")
//    assertPackagesStrippedAs("package a\npackage b", "")
//
//    assertPackagesStrippedAs("package a\nclass C", "class C")
//    assertPackagesStrippedAs("package a\npackage b\nclass C", "class C")
//
//    assertPackagesStrippedAs("package a {\nclass A\npackage b {\nclass B\n}\n}", "class A\nclass B\n")
//    assertPackagesStrippedAs("package a {\npackage b {\nclass A\n}\npackage c {\nclass B\n}\n}", "class A\nclass B\n")
//  }
//
//  def testStripPackagesIdentity() {
//    val file = parseText("package foo\nclass C")
//    val oldClass = file.depthFirst.findByType(classOf[ScClass]).get
//    ScalaFileImpl.stripPackagesIn(file)
//    val newClass = file.getFirstChild
//    Assert.assertSame(oldClass, newClass)
//    Assert.assertSame(file, newClass.getContainingFile)
//  }

  def testPathIn(): Unit = {
    assertPathIs("", List())
    assertPathIs("package a", List(List("a")))
    assertPathIs("package a.b", List(List("a", "b")))
    assertPathIs("package a\npackage b", List(List("a"), List("b")))
    assertPathIs("package a.b\npackage c.d", List(List("a", "b"), List("c", "d")))

    assertPathIs("/* foo */\npackage a", List(List("a")))
    assertPathIs("/* foo */\npackage a\n/* bar */\npackage b", List(List("a"), List("b")))
  }

//  def testSetPath() {
//    assertPathAddedAs("", List(List("a")), "package a");
//    assertPathAddedAs("", List(List("a", "b")), "package a.b");
//    assertPathAddedAs("", List(List("a"), List("b")), "package a\npackage b");
//    assertPathAddedAs("", List(List("a", "b"), List("c", "d")), "package a.b\npackage c.d");
//
//    assertPathAddedAs("class C", List(List("a")), "package a\n\nclass C");
//    assertPathAddedAs("class C", List(List("a"), List("b")), "package a\npackage b\n\nclass C");
//
////    assertPathAddedAs("class C\n\n", List(List("a")), "package a\n\nclass C\n\n");
//
////    assertPathAddedAs(" ", List(List("a")), "package a\n\n ");
//  }

//  def testSetPathIdentity() {
//    val file = parseText("class C")
//    val oldClass = file.getFirstChild
//    ScalaFileImpl.addPathTo(file, List(List("foo")))
//    val newClass = file.depthFirst.findByType(classOf[ScClass]).get
//    Assert.assertSame(oldClass, newClass)
//    Assert.assertSame(file, newClass.getContainingFile)
//  }

  def testSplitsIn(): Unit = {
    assertSplitsAre(List(), List())
    assertSplitsAre(List(List("a")), List())
    assertSplitsAre(List(List("a", "b")), List())
    assertSplitsAre(List(List("a", "b", "c")), List())
    assertSplitsAre(List(List("a"), List("b")), List(List("a")))
    assertSplitsAre(List(List("a"), List("b"), List("c")), List(List("a"), List("a", "b")))
    assertSplitsAre(List(List("a", "b"), List("c", "d")), List(List("a", "b")))
    assertSplitsAre(List(List("a", "b"), List("c", "d"), List("e")), List(List("a", "b"), List("a", "b", "c", "d")))
  }

  def testSplitAt(): Unit = {
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

/*  def testSetPackageName() {
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

  def testSetPackageNameWithFileComments() {
    assertPackageNameSetAs("// comment\npackage foo\n\nclass C", ("", "bar"), "// comment\npackage bar\n\nclass C");
    assertPackageNameSetAs("// comment\n\npackage foo\n\nclass C", ("", "bar"), "// comment\n\npackage bar\n\nclass C");
    assertPackageNameSetAs("// comment 1\n// comment 2\npackage foo\n\nclass C", ("", "bar"), "// comment 1\n// comment 2\npackage bar\n\nclass C");
    assertPackageNameSetAs("/* block */\npackage foo\n\nclass C", ("", "bar"), "/* block */\npackage bar\n\nclass C");
    assertPackageNameSetAs("/** doc */\npackage foo\n\nclass C", ("", "bar"), "/** doc */\npackage bar\n\nclass C");
  }

  def testSetPackageNameIdentity() {
    val file = parseText("package foo\npackage bar\nclass C").asInstanceOf[ScalaFileImpl]
    val oldClass = file.depthFirst.findByType(classOf[ScClass]).get
    file.setPackageName("moo.goo", "")
    val newClass = file.depthFirst.findByType(classOf[ScClass]).get
    Assert.assertSame(oldClass, newClass)
    Assert.assertSame(file, newClass.getContainingFile)
  }*/
  
//  private def assertPackagesStrippedAs(before: String, after: String) {
//    val file = parseText(before)
//    ScalaFileImpl.stripPackagesIn(file)
//    Assert.assertEquals(describe(parseText(after)), describe(file))
//  }

  private def assertPathIs(code: String, path: List[List[String]]): Unit = {
    Assert.assertEquals(path, ScalaFileImpl.pathIn(parseText(code)))
  }

//  private def assertPathAddedAs(before: String, path: List[List[String]], after: String) {
//    val file = parseText(before)
//    ScalaFileImpl.addPathTo(file, path)
//    Assert.assertEquals(describe(parseText(after)), describe(file))
//  }

  private def assertSplitAs(before: List[List[String]], vector: List[String], after: List[List[String]]): Unit = {
    Assert.assertEquals(after, ScalaFileImpl.splitAt(before, vector))
  }

  private def assertSplitsAre(path: List[List[String]], vectors: List[List[String]]): Unit = {
    Assert.assertEquals(vectors, ScalaFileImpl.splitsIn(path))
  }

  private def assertPackageNameSetAs(before: String, name: (String,  String), after: String ): Unit = {
    val file = parseText(before).asInstanceOf[ScalaFileImpl]
    file.setPackageName(name._1, name._2)
    Assert.assertEquals(describe(parseText(after)), describe(file))
  }
}