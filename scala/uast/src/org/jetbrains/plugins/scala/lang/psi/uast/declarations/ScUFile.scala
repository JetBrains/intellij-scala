package org.jetbrains.plugins.scala.lang.psi.uast.declarations

import java.{util => javacoll}

import com.intellij.psi.{PsiComment, PsiFile, PsiRecursiveElementVisitor}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.ScalaUastLanguagePlugin
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.utils.JavaCollectionsCommon
import org.jetbrains.uast._

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
  * [[ScalaFile]] adapter for the [[UFile]]
  *
  * @param scElement      Scala PSI element representing Scala file
  * @param languagePlugin Instance of [[ScalaUastLanguagePlugin]]
  */
class ScUFile(override protected val scElement: ScalaFile,
              languagePlugin: UastLanguagePlugin)
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

  override def getLanguagePlugin: UastLanguagePlugin = languagePlugin

  override def getClasses: javacoll.List[UClass] =
    seqAsJavaList(scElement.typeDefinitions.flatMap(_.convertTo[UClass](this)))

  // TODO: only top level imports are converted - should all imports be converted?
  override def getImports: javacoll.List[UImportStatement] = {
    val fileImports = scElement.getImportStatements
    val fstPackageImports =
      scElement.firstPackaging.map(_.getImportStatements).getOrElse(Seq())

    (fileImports ++ fstPackageImports)
      .flatMap(_.convertTo[UImportStatement](this))
      .asJava
  }

  override def getAllCommentsInFile: javacoll.List[UComment] = {
    val buf = ArrayBuffer.empty[UComment]
    scElement.accept(new PsiRecursiveElementVisitor() {
      override def visitComment(comment: PsiComment): Unit =
        buf += BaseScala2UastConverter.createUComment(comment, thisFile)
    })
    buf.asJava
  }

  override def getPackageName: String = scElement.getPackageName

  // Scala files cannot have annotations
  override def getUAnnotations: javacoll.List[UAnnotation] =
    JavaCollectionsCommon.newEmptyJavaList
}
