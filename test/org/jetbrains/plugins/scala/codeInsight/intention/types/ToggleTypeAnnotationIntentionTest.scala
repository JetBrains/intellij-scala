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
}
