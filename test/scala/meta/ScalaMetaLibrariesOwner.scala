package scala.meta

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyLibraryLoaderAdapter, ThirdPartyLibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaSdkOwner, ScalaVersion, Scala_2_11_11}

trait ScalaMetaLibrariesOwner extends ScalaSdkOwner {

  override implicit val version: ScalaVersion = Scala_2_11_11

  import ScalaMetaLibrariesOwner._

  protected def additionalLibraries(): Array[ThirdPartyLibraryLoader] = Array(
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
    MetaSemanticLoader()
  )
}

object ScalaMetaLibrariesOwner {

  abstract class MetaBaseLoader(implicit module: Module) extends IvyLibraryLoaderAdapter {
    override protected val version: String = "1.6.0"
    override protected val vendor: String = "org.scalameta"

    override protected def path(implicit version: ScalaVersion): String =
      super.path(version)
  }

  private case class MetaCommonLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "common"
  }

  private case class MetaDialectsLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "dialects"
  }

  private case class MetaInlineLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "inline"
  }

  private case class MetaInputsLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "inputs"
  }

  private case class MetaParsersLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "parsers"
  }

  private case class MetaQuasiquotesLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "quasiquotes"
  }

  private case class MetaScalametaLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "scalameta"
  }

  private case class MetaTokenizersLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "tokenizers"
  }

  private case class MetaTokensLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "tokens"
  }

  private case class MetaTransversersLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "transversers"
  }

  private case class MetaTreesLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "trees"
  }

  private case class MetaSemanticLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "semantic"
  }

}
