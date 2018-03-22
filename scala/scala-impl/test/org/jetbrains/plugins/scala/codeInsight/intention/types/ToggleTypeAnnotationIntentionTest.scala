package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class ToggleTypeAnnotationIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = ToggleTypeAnnotation.FamilyName

  def testCollectionFactorySimplification(): Unit = doTest(
    "val v = Seq.empty[String]",
    "val v: Seq[String] = Seq.empty"
  )

  def testCollectionFactoryNoSimplification(): Unit = doTest(
    "val v = Seq.empty[String].to[Seq]",
    "val v: Seq[String] = Seq.empty[String].to[Seq]"
  )

  def testOptionFactorySimplification(): Unit = doTest(
    "val v = Option.empty[String]",
    "val v: Option[String] = Option.empty"
  )

  def testOptionFactoryNoSimplification(): Unit = doTest(
    "val v = Option.empty[String].to[Option]",
    "val v: Option[String] = Option.empty[String].to[Option]"
  )
   
  def testCompoundType(): Unit = doTest(
    """
      |val foo = new Runnable {
      |  def helper(): Unit = ???
      |
      |  override def run(): Unit = ???
      |}""".stripMargin,
    """
      |val foo: Runnable = new Runnable {
      |  def helper(): Unit = ???
      |
      |  override def run(): Unit = ???
      |}""".stripMargin
  )
  
  def testCompoundTypeWithTypeMember(): Unit = doTest(
    s"""
       |trait Foo {
       |  type X
       |}
       |
       |val f${caretTag}oo = new Foo {
       |  override type X = Int
       |  
       |  def helper(x: X): Unit = ???
       |}
     """.stripMargin,
    s"""
       |trait Foo {
       |  type X
       |}
       |
       |val f${caretTag}oo: Foo {
       |  type X = Int
       |} = new Foo {
       |  override type X = Int
       |
       |  def helper(x: X): Unit = ???
       |}
     """.stripMargin
  )
  
  def testInfixType(): Unit = doTest(
    s"""
       |trait A
       |
       |trait B
       |
       |def foo(): =:=[A, <:<[B, =:=[=:=[B, B], A]]] = ???
       |val ba${caretTag}r = foo()
     """.stripMargin,
    s"""
       |trait A
       |
       |trait B
       |
       |def foo(): =:=[A, <:<[B, =:=[=:=[B, B], A]]] = ???
       |val ba${caretTag}r: A =:= (B <:< (B =:= B =:= A)) = foo()
     """.stripMargin
  )
  
  def testInfixDifferentAssociativity(): Unit = doTest(
    s"""
       |trait +[A, B]
       |
       |trait ::[A, B]
       |
       |trait A
       |
       |def foo(): ::[+[A, +[::[A, A], A]], +[A, ::[A, A]]] = ???
       |val ba${caretTag}r = foo()
     """.stripMargin,
    s"""
       |trait +[A, B]
       |
       |trait ::[A, B]
       |
       |trait A
       |
       |def foo(): ::[+[A, +[::[A, A], A]], +[A, ::[A, A]]] = ???
       |val ba${caretTag}r: (A + ((A :: A) + A)) :: (A + (A :: A)) = foo()
     """.stripMargin
  )
}
