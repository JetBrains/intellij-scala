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

  abstract class MetaBaseLoader extends IvyLibraryLoaderAdapter {
    override protected val version: String = metaVersion
    override val vendor: String = "org.scalameta"

    override def path(implicit version: ScalaVersion): String =
      super.path(version)
  }

  private case class MetaCommonLoader() extends MetaBaseLoader {
    override val name: String = "common"
  }

  private case class MetaDialectsLoader() extends MetaBaseLoader {
    override val name: String = "dialects"
  }

  private case class MetaInlineLoader() extends MetaBaseLoader {
    override val name: String = "inline"
  }

  private case class MetaInputsLoader() extends MetaBaseLoader {
    override val name: String = "inputs"
  }

  private case class MetaParsersLoader() extends MetaBaseLoader {
    override val name: String = "parsers"
  }

  private case class MetaQuasiquotesLoader() extends MetaBaseLoader {
    override val name: String = "quasiquotes"
  }

  private case class MetaScalametaLoader() extends MetaBaseLoader {
    override val name: String = "scalameta"
  }

  private case class MetaTokenizersLoader() extends MetaBaseLoader {
    override val name: String = "tokenizers"
  }

  private case class MetaTokensLoader() extends MetaBaseLoader {
    override val name: String = "tokens"
  }

  private case class MetaTransversersLoader() extends MetaBaseLoader {
    override val name: String = "transversers"
  }

  private case class MetaTreesLoader() extends MetaBaseLoader {
    override val name: String = "trees"
  }

  private case class MetaSemanticLoader() extends MetaBaseLoader {
    override val name: String = "semantic"
  }

  private case class MetaIOLoader() extends MetaBaseLoader {
    override val name: String = "io"
  }

  private case class FastParseLoader() extends IvyLibraryLoaderAdapter {
    override val version: String = "0.4.3"
    override val name: String = "fastparse"
    override val vendor: String = "com.lihaoyi"
  }

}
