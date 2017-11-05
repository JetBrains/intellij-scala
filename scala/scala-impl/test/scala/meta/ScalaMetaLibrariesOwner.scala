package scala.meta

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyLibraryLoaderAdapter, ThirdPartyLibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaSdkOwner, ScalaVersion, Scala_2_12}

trait ScalaMetaLibrariesOwner extends ScalaSdkOwner {

  override implicit val version: ScalaVersion = Scala_2_12

  import ScalaMetaLibrariesOwner._

  protected def additionalLibraries(): Seq[ThirdPartyLibraryLoader] = Seq(
    MetaCommonLoader(),
    MetaDialectsLoader(),
    MetaInlineLoader(),
    MetaInputsLoader(),
    MetaParsersLoader(),
    MetaQuasiquotesLoader(),
    MetaScalametaLoader(),
    MetaTokenizersLoader(),
    MetaTokensLoader(),
    MetaTransversersLoader(),
    MetaTreesLoader(),
    MetaSemanticLoader(),
    MetaIOLoader(),
    FastParseLoader()
  )
}

object ScalaMetaLibrariesOwner {

  val metaVersion = "1.8.0"

  abstract class MetaBaseLoader(implicit module: Module) extends IvyLibraryLoaderAdapter {
    override protected val version: String = metaVersion
    override val vendor: String = "org.scalameta"

    override def path(implicit version: ScalaVersion): String =
      super.path(version)
  }

  private case class MetaCommonLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "common"
  }

  private case class MetaDialectsLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "dialects"
  }

  private case class MetaInlineLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "inline"
  }

  private case class MetaInputsLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "inputs"
  }

  private case class MetaParsersLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "parsers"
  }

  private case class MetaQuasiquotesLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "quasiquotes"
  }

  private case class MetaScalametaLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "scalameta"
  }

  private case class MetaTokenizersLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "tokenizers"
  }

  private case class MetaTokensLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "tokens"
  }

  private case class MetaTransversersLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "transversers"
  }

  private case class MetaTreesLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "trees"
  }

  private case class MetaSemanticLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "semantic"
  }

  private case class MetaIOLoader()(implicit val module: Module) extends MetaBaseLoader {
    override val name: String = "io"
  }

  private case class FastParseLoader()(implicit val module: Module) extends IvyLibraryLoaderAdapter {
    override val version: String = "0.4.3"
    override val name: String = "fastparse"
    override val vendor: String = "com.lihaoyi"
  }

}
