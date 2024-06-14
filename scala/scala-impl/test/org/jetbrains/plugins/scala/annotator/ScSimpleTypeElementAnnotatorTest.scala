package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.element.ScSimpleTypeElementAnnotator
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScSimpleTypeElementAnnotatorTest extends AnnotatorSimpleTestCase {
  override protected def scalaVersion: ScalaVersion = LatestScalaVersions.Scala_3

  def testTraitConstructor(): Unit =
    assertNothing(messages(
      """
        |object A {
        |
        |  given givenSomething:String = "asd"
        |
        |  trait Example[A]
        |
        |
        |  trait TraitSimple[T[_]]
        |
        |  trait TraitWithArgs[T[_]](using something:String)
        |
        |  abstract class AbstractClassWithArgs[T[_]](using something: String)
        |
        |  // it works if the trait does not have parameters
        |  object instance
        |    extends TraitSimple[Example]
        |
        |  // it does not work when the trait has parameters (even implicit parameters)
        |  object instanceFailing
        |    extends TraitWithArgs[Example]
        |
        |  // it work if it is an abstract class
        |  object instanceWorking
        |    extends AbstractClassWithArgs[Example]
        |}
        |""".stripMargin
    ))

  def messages(code: String): List[Message] = {
    val file: ScalaFile = parseScalaFile(code, scalaVersion)
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().filterByType[ScSimpleTypeElement].foreach { pte =>
      ScSimpleTypeElementAnnotator.annotate(pte, typeAware = true)
    }

    mock.annotations
  }
}
