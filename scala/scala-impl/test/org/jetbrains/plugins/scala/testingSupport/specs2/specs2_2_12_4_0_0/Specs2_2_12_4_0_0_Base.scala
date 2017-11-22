package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_12_4_0_0

import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
  * @author Roman.Shein
  * @since 11.01.2015.
  */
trait Specs2_2_12_4_0_0_Base extends Specs2TestCase {

  override implicit val version: ScalaVersion = Scala_2_12

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    import Specs2_2_12_4_0_0_Base._

    Seq(Specs2CommonLoader(), Specs2CoreLoader(), Specs2MatcherLoader(), Specs2FpLoader(), ScalaXmlLoader())
  }
}

object Specs2_2_12_4_0_0_Base {

  abstract class Specs2_4_BaseLoader extends Specs2BaseLoader {
    override val version: String = "4.0.0"
  }

  case class Specs2CommonLoader() extends Specs2_4_BaseLoader {
    override val name: String = "specs2-common"
  }

  case class Specs2CoreLoader() extends Specs2_4_BaseLoader {
    override val name: String = "specs2-core"
  }

  case class Specs2MatcherLoader() extends Specs2_4_BaseLoader {
    override val name: String = "specs2-matcher"
  }

  case class Specs2FpLoader() extends Specs2_4_BaseLoader {
    override val name: String = "specs2-fp"
  }
}