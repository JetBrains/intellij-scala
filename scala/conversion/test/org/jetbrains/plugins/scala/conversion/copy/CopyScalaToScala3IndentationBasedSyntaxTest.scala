package org.jetbrains.plugins.scala.conversion.copy

import org.jetbrains.plugins.scala.ScalaVersion

class CopyScalaToScala3IndentationBasedSyntaxTest extends CopyPasteTestBase {

  // copied from Scala3IndentationBasedSyntaxBackspaceTest
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  def testInnerMethod(): Unit = {
    val from =
      s"""${Start}def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_1(): Unit = {
    val from =
      s"""${Start}def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_2(): Unit = {
    val from =
      s"""$Start
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_3(): Unit = {
    val from =
      s"""$Start
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_FromObject(): Unit = {
    val from =
      s"""object Example:
         |$Start  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_FromObject_1(): Unit = {
    val from =
      s"""object Example:
         |$Start  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_FromObject_2(): Unit = {
    val from =
      s"""object Example:
         |$Start
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |  $Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |
         |"""

    doTestWithStrip(from, to, after)
  }

  def testInnerMethod_FromObject_3(): Unit = {
    val from =
      s"""object Example:
         |$Start
         |  def foo() =
         |    def baz() =
         |      print(1)
         |    baz(1)
         |$End
         |"""
    val to =
      s"""def bar() =
         |  print(2)
         |$Caret
         |"""
    val after =
      s"""def bar() =
         |  print(2)
         |
         |def foo() =
         |  def baz() =
         |    print(1)
         |  baz(1)
         |
         |"""

    doTestWithStrip(from, to, after)
  }
}
