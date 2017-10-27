package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_12_4_0_0

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoader.{Bundles, IvyType}
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

    implicit val module: Module = getModule
    Seq(Specs2CommonLoader(), Specs2CoreLoader(), Specs2MatcherLoader())
  }
}

object Specs2_2_12_4_0_0_Base {

  abstract class Specs2_3_BaseLoader(implicit module: Module) extends Specs2BaseLoader {
    override val version: String = "4.0.0"
  }

  case class Specs2CommonLoader()(implicit val module: Module) extends Specs2_3_BaseLoader {
    override val name: String = "specs2-common"
  }

  case class Specs2CoreLoader()(implicit val module: Module) extends Specs2_3_BaseLoader {
    override val name: String = "specs2-core"
  }

  case class Specs2MatcherLoader()(implicit val module: Module) extends Specs2_3_BaseLoader {
    override val name: String = "specs2-matcher"
  }

  case class ScalaZEffectLoader()(implicit val module: Module) extends ScalaZBaseLoader {
    override val name: String = "scalaz-effect"
  }

  case class ScalaZStreamLoader()(implicit val module: Module) extends ScalaZBaseLoader {
    override val name: String = "scalaz-stream"
    override val vendor: String = "org.scalaz.stream"
    override val version: String = "0.6a"
  }

  case class ShapelessLoader()(implicit val module: Module) extends IvyLibraryLoaderAdapter {
    override val name: String = "shapeless"
    override val vendor: String = "com.chuusai"
    override val version: String = "2.0.0"
    override val ivyType: IvyType = Bundles
  }

  abstract class SCodecBaseLoader(implicit module: Module) extends IvyLibraryLoaderAdapter {
    override val vendor: String = "org.typelevel"
    override val ivyType: IvyType = Bundles
  }

  case class SCodecCoreLoader()(implicit val module: Module) extends SCodecBaseLoader {
    override val name: String = "scodec-core"
    override val version: String = "1.7.0-SNAPSHOT"
  }

  case class SCodecBitsLoader()(implicit val module: Module) extends SCodecBaseLoader {
    override val name: String = "scodec-bits"
    override val version: String = "1.1.0-SNAPSHOT"
  }

}