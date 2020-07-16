package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

@ApiStatus.Internal
trait ScalaImportOptimizerHelper {

  def isImportUsed(used: ImportUsed): Boolean

  def cannotShadowName(info: ImportInfo): Boolean

  /** @return if true, expr shouldn't be merged */
  def preventMerging(info: ImportInfo): Boolean
}

object ScalaImportOptimizerHelper {
  private val CLASS_NAME = "org.intellij.scala.importOptimizerHelper"

  private val EP_NAME: ExtensionPointName[ScalaImportOptimizerHelper] =
    ExtensionPointName.create(CLASS_NAME)

  def extensions: Iterable[ScalaImportOptimizerHelper] =
    EP_NAME.getExtensionList.asScala
}
