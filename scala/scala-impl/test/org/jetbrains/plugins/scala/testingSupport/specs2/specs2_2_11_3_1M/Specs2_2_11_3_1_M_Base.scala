package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_11_3_1M

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoader.{Bundles, IvyType}
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
  * @author Roman.Shein
  * @since 11.01.2015.
  */
trait Specs2_2_11_3_1_M_Base extends Specs2TestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    import Specs2_2_11_3_1_M_Base._

    Seq(Specs2CommonLoader(), Specs2CoreLoader(), Specs2MatcherLoader(),
      ScalaZCoreLoader(), ScalaZConcurrentLoader(), ScalaZEffectLoader(), ScalaZStreamLoader(),
      ScalaXmlLoader(),
      SCodecBitsLoader(), SCodecCoreLoader())
  }
}

object Specs2_2_11_3_1_M_Base {

  abstract class Specs2_3_BaseLoader extends Specs2BaseLoader {
    override val version: String = "3.0.1"
  }

  case class Specs2CommonLoader() extends Specs2_3_BaseLoader {
    override val name: String = "specs2-common"
  }

  case class Specs2CoreLoader() extends Specs2_3_BaseLoader {
    override val name: String = "specs2-core"
  }

  case class Specs2MatcherLoader() extends Specs2_3_BaseLoader {
    override val name: String = "specs2-matcher"
  }

  case class ScalaZEffectLoader() extends ScalaZBaseLoader {
    override val name: String = "scalaz-effect"
  }

  case class ScalaZStreamLoader() extends ScalaZBaseLoader {
    override val name: String = "scalaz-stream"
    override val vendor: String = "org.scalaz.stream"
    override val version: String = "0.6a"
  }

  case class ShapelessLoader() extends IvyLibraryLoaderAdapter {
    override val name: String = "shapeless"
    override val vendor: String = "com.chuusai"
    override val version: String = "2.0.0"
    override val ivyType: IvyType = Bundles
  }

  abstract class SCodecBaseLoader extends IvyLibraryLoaderAdapter {
    override val vendor: String = "org.typelevel"
    override val ivyType: IvyType = Bundles
  }

  case class SCodecCoreLoader() extends SCodecBaseLoader {
    override val name: String = "scodec-core"
    override val version: String = "1.7.0-SNAPSHOT"
  }

  case class SCodecBitsLoader() extends SCodecBaseLoader {
    override val name: String = "scodec-bits"
    override val version: String = "1.1.0-SNAPSHOT"
  }

}