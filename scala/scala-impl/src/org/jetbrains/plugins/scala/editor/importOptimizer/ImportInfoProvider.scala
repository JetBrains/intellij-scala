package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.plugins.scala.extensions.{OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportUsed}

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

@Experimental
trait ImportInfoProvider {

  def acceptsFile(file: ScalaFile): Boolean

  def isImportUsed(imp: ScImportExpr): Boolean

  final def isImportUsedWithFileCheck(imp: ScImportExpr): Boolean =
    if (imp.containingFile.filterByType[ScalaFile].exists(acceptsFile))
      isImportUsed(imp)
    else
      false
}

object ImportInfoProvider {
  private val CLASS_NAME = "org.intellij.scala.importUsedProvider"

  private val EP_NAME: ExtensionPointName[ImportInfoProvider] =
    ExtensionPointName.create(CLASS_NAME)

  def providers: Iterable[ImportInfoProvider] =
    ImportInfoProvider.EP_NAME.getExtensionList.asScala

  def filterOutUsedImports(file: ScalaFile, unused: Seq[ImportUsed]): Seq[ImportUsed] =
    providers.foldLeft(unused) { case (acc, provider) =>
      if (provider.acceptsFile(file))
        acc.filterNot {
          case ImportExprUsed(ex) => provider.isImportUsed(ex)
          case _                  => false
        }
      else
        acc
    }
}
