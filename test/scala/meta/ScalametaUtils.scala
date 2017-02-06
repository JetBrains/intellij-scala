package scala.meta

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyLibraryLoaderAdapter, ThirdPartyLibraryLoader}
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

trait ScalametaUtils extends ScalaVersion {

  protected override def scalaSdkVersion: ScalaSdkVersion = ScalaSdkVersion._2_11_8

  import ScalametaUtils._

  protected def additionalLibraries(module: Module): Array[ThirdPartyLibraryLoader] = {
    implicit val m = module

    Array(
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
      MetaTreesLoader()
    )
  }

  protected def enableParadisePlugin(module: Module)
                                    (implicit project: Project): Unit = {
    val profile = ScalaCompilerConfiguration.instanceIn(project).defaultProfile
    val settings = profile.getSettings

    settings.plugins :+= MetaParadiseLoader()(module).path
    profile.setSettings(settings)
  }
}

object ScalametaUtils {

  private abstract class MetaBaseLoader(implicit module: Module) extends IvyLibraryLoaderAdapter {
    override protected val version: String = "1.3.0"
    override protected val vendor: String = "org.scalameta"

    override protected def path(implicit version: ScalaSdkVersion): String =
      super.path(ScalaSdkVersion._2_11_8)
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

  private case class MetaParadiseLoader(implicit val module: Module) extends MetaBaseLoader {
    override protected val name: String = "paradise"
    override protected val version: String = "3.0.0-M5"

    override protected def folder(implicit version: ScalaSdkVersion): String =
      s"${name}_${version.getMinor}"


    override def path(implicit version: ScalaSdkVersion): String = super.path

    override def init(implicit version: ScalaSdkVersion): Unit = {}
  }

}
