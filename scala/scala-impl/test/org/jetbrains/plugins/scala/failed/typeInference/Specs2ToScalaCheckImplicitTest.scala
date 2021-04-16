package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Nikolay.Tropin
  */
class Specs2ToScalaCheckImplicitTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override protected def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+
      IvyManagedLoader(
        "org.specs2" %% "specs2" % "2.4.15",
        "org.scalaz" %% "scalaz-core" % "7.1.0",
      )

  def testSCL8864(): Unit = doTest {
    s"""object Main extends App {
       |  import org.specs2.ScalaCheck
       |  import org.specs2.mutable.Specification
       |
      |  class Foo extends Specification with ScalaCheck {
       |    prop { (numbers: Seq[Int]) =>
       |      numbers.nonEmpty ==> {
       |        ${START}numbers.sum / numbers.size must be_>(1)$END
       |      }
       |    }
       |  }
       |}
       |//Prop
    """.stripMargin
  }

}
