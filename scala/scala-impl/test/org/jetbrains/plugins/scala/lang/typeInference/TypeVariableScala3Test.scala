package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class TypeVariableScala3Test extends TypeInferenceTestBase {
  /*
   * This currently fails, because of the way desugaring for infix type elements works.
   * After desugaring (which is needed to calculate type of the type element) we end up with
   * a new set of type variables, with typeIds different from the original ones (present in the source code).
   */
  override protected def shouldPass = false

  private final val Prefix =
    "class Type; " +
    "class &&[A, B]; "

  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  def testInfix1(): Unit = checkTextHasNoErrors(Prefix +
    "(??? : Unit && Type) match { case _: (t1 && t2) => val v: Type = ??? : t2 }")

  def testInfix2(): Unit = checkTextHasNoErrors(Prefix +
    "(??? : Unit && Type) match { case _: (t1 && t2) => val v: t2 = ??? : Type }")
}
