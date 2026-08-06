package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
trait ImplicitAmbiguityTreatmentTest extends ImplicitParametersTestBase {
  def testAmbiguity(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  class A
       |  class B extends C
       |  class C
       |  implicit def a1: A = ???
       |  implicit def a2: A = ???
       |  implicit def b(implicit a: A): B = ???
       |  implicit def c: C = ???
       |
       |  ${START}implicitly[C]$END
       |}
       |""".stripMargin
  )
}

class ImplicitAmbiguityTreatmentTestScala3 extends ImplicitAmbiguityTreatmentTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_LTS

  override protected def shouldPass: Boolean = false
}

class ImplicitAmbiguityTreatmentTestScala2 extends ImplicitAmbiguityTreatmentTest
