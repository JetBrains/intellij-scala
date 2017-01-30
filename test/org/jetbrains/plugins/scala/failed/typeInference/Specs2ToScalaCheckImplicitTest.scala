package org.jetbrains.plugins.scala.failed.typeInference

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.libraryLoaders.{ScalaZLoader, Specs2Loader, ThirdPartyLibraryLoader}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class Specs2ToScalaCheckImplicitTest extends TypeInferenceTestBase {

  override protected def additionalLibraries(module: Module): Array[ThirdPartyLibraryLoader] =
    Array(Specs2Loader()(module), ScalaZLoader()(module))

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
