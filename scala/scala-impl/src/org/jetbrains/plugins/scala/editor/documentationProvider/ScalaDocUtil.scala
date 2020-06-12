package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.{PsiClass, PsiDocCommentOwner, PsiElement}
import org.jetbrains.plugins.scala.extensions.{PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

private object ScalaDocUtil {

  def shortestClassName(clazz: PsiClass, context: PsiElement): String =
    shortestClassNameImpl(clazz, context)

  def shortestClassName(typeAlias: ScTypeAlias, context: PsiElement): String =
    shortestClassNameImpl(typeAlias, context)

  private trait QualifiedNameOwner[T <: PsiElement] {
    def name(element: T): String
    def qualifiedNameOpt(element: T): Option[String]
    def containingClass(element: T): PsiClass
  }

  /** by analogy with [[com.intellij.codeInsight.javadoc.JavaDocUtil.getShortestClassName]] */
  private def shortestClassNameImpl[T <: PsiElement: QualifiedNameOwner](element: T, context: PsiElement): String = {
    import QualifiedNameOwner.QualifiedNameOwnerExt
    var shortName = element.name
    if (shortName == null) shortName = "null"

    var containingClass: PsiClass = element.containingClass
    while (containingClass != null &&
      containingClass.isPhysical &&
      !PsiUtil.isLocalOrAnonymousClass(containingClass) &&
      containingClass.name != "scala.Predef" && containingClass.name != "scala"
    ) {
      shortName = containingClass.name + "." + shortName
      containingClass = containingClass.containingClass
    }

    val qualifiedName = element.qualifiedNameOpt match {
      case Some(fqn) => fqn
      case None      => return shortName
    }
    val ref = ScalaPsiElementFactory.createDocReferenceFromText(shortName, context, null)
    val resolveResults = ref.multiResolveScala(incomplete = false)
    val manager = element.getManager
    val resolved = resolveResults.find(r => manager.areElementsEquivalent(element, r.element))
    if (resolved.nonEmpty)
      shortName
    else
      qualifiedName
  }

  private object QualifiedNameOwner {
    def apply[T <: PsiElement](implicit ev: QualifiedNameOwner[T]): QualifiedNameOwner[T] = ev

    implicit class QualifiedNameOwnerExt[T  <: PsiElement : QualifiedNameOwner](private val owner: T) {
      def name: String = QualifiedNameOwner[T].name(owner)
      def qualifiedNameOpt: Option[String] = QualifiedNameOwner[T].qualifiedNameOpt(owner)
      def containingClass: PsiClass = QualifiedNameOwner[T].containingClass(owner)
    }

    implicit val clazzImpl: QualifiedNameOwner[PsiClass] = new QualifiedNameOwner[PsiClass] {
      override def name(element: PsiClass): String = element.name
      override def qualifiedNameOpt(element: PsiClass): Option[String] = element.qualifiedNameOpt
      override def containingClass(element: PsiClass): PsiClass = element.containingClass
    }
    implicit val typeAlias: QualifiedNameOwner[ScTypeAlias] = new QualifiedNameOwner[ScTypeAlias] {
      override def name(element: ScTypeAlias): String = element.name
      override def qualifiedNameOpt(element: ScTypeAlias): Option[String] = element.qualifiedNameOpt
      override def containingClass(element: ScTypeAlias): PsiClass = element.containingClass
    }
  }

  def generateJavaDocInfoContentInner(element: PsiDocCommentOwner): Option[String] = {
    val javadoc = generateJavaDocInfo(element)
    val javadocContent = extractJavaDocContentInner(javadoc)
    javadocContent
  }

  def generateJavaDocInfoContentWithSections(element: PsiDocCommentOwner): String = {
    val javadoc = generateJavaDocInfo(element)
    val javadocContent = extractJavaDocContentWithSectionsParts(javadoc)
    javadocContent
  }

  private def generateJavaDocInfo(element: PsiElement): String = {
    val builder = new java.lang.StringBuilder()
    val generator = new JavaDocInfoGenerator(element.getProject, element)
    generator.generateDocInfoCore(builder, false)
    builder.toString
  }

  // TODO: this is far from perfect to rely on text... =(
  //  dive deep into Javadoc generation and implement in a more safe and structural way
  private def extractJavaDocContentWithSectionsParts(javadoc: String): String = {
    val contentStartIdx = javadoc.indexOf(DocumentationMarkup.CONTENT_START) match {
      case -1 => javadoc.indexOf(DocumentationMarkup.SECTIONS_START)
      case idx => idx
    }
    if (contentStartIdx > 0) javadoc.substring(contentStartIdx)
    else javadoc
  }

  private val ContentEnd1 = s"</div>${DocumentationMarkup.SECTIONS_START}"
  private val ContentEnd2 = "</div></body>"

  private def extractJavaDocContentInner(javadoc: String): Option[String] = {
    val contentStartIdx = javadoc.indexOf(DocumentationMarkup.CONTENT_START) match {
      case -1 => return None
      case idx => idx + DocumentationMarkup.CONTENT_START.length
    }
    val contentEndIndex = javadoc.indexOf(ContentEnd1) match {
      case -1 => javadoc.indexOf(ContentEnd2)
      case idx => idx
    }
    if (contentEndIndex == -1)
      return None

   Some(javadoc.substring(contentStartIdx, contentEndIndex))
  }
}
