package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class TypeVariableScala3Test extends TypeInferenceTestBase {
  /*
   * This currently fails, because of the way desugaring for infix type elements works.
   * After desugaring (which is needed to calculate type of the type element) we end up with
   * a new set of type variables, with typeIds different from the original ones (present in the source code).
   */

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testInfix1(): Unit = assertErrorsText(
    """class Type
      |class &&[A, B]
      |(??? : Unit && Type) match { case _: (t1 && t2) => val v: Type = ??? : t2 }""".stripMargin,
    //FIXME: this is wrong expected data, see comment above
    """Error(t2,Expression of type t2 doesn't conform to expected type Type)"""
  )

  def testInfix2(): Unit = assertErrorsText(
    """class Type
      |class &&[A, B]
      |(??? : Unit && Type) match { case _: (t1 && t2) => val v: t2 = ??? : Type }""".stripMargin,
    //FIXME: this is wrong expected data, see comment above
    """Error(Type,Expression of type Type doesn't conform to expected type t2)"""
  )
}
