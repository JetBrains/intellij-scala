package org.jetbrains.plugins.scala
package lang
package psi
package uast
package declarations

import java.{util => ju}

import com.intellij.psi.{PsiComment, PsiFile, PsiRecursiveElementVisitor}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.uast.ScalaUastLanguagePlugin
import org.jetbrains.uast._

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * [[ScalaFile]] adapter for the [[UFile]]
 *
 * @param scElement         Scala PSI element representing Scala file
 * @param getLanguagePlugin Instance of [[ScalaUastLanguagePlugin]]
 */
final class ScUFile(override protected val scElement: ScalaFile,
                    override val getLanguagePlugin: ScalaUastLanguagePlugin)
    extends UFileAdapter
    with ScUElement {
  thisFile =>

  override type PsiFacade = PsiFile

  override protected def parent: LazyUElement =
    LazyUElement.Empty

  @Nullable
  override def getJavaPsi: PsiFile = null

  @Nullable
  override def getSourcePsi: PsiFile = scElement

  override def isPsiValid: Boolean = scElement.isValid

  override def getClasses: ju.List[UClass] =
    scElement.typeDefinitions.flatMap(_.convertTo[UClass](this)).asJava

  // TODO: only top level imports are converted - should all imports be converted?
  override def getImports: ju.List[UImportStatement] = {
    val fileImports = scElement.getImportStatements
    val fstPackageImports =
      scElement.firstPackaging.map(_.getImportStatements).getOrElse(Seq())

    (fileImports ++ fstPackageImports)
      .flatMap(_.convertTo[UImportStatement](this))
      .asJava
  }

  override def getAllCommentsInFile: ju.List[UComment] = {
    val buf = ArrayBuffer.empty[UComment]
    scElement.accept(new PsiRecursiveElementVisitor() {
      override def visitComment(comment: PsiComment): Unit =
        buf += Scala2UastConverter.createUComment(comment, thisFile)
    })
    buf.asJava
  }

  override def getPackageName: String = scElement.getPackageName

  // Scala files cannot have annotations
  override def getUAnnotations: ju.List[UAnnotation] = ju.Collections.emptyList()
}
