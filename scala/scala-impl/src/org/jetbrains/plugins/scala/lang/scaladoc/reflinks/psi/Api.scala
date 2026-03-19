package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPathElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.resolve.ResolvableStableCodeReference
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference

sealed trait ScDocRefQuery extends ResolvableStableCodeReference with ScPathElement

object ScDocRefQuery {
  private val backSlashReplaceRegex = raw"\\(.)".r
  def cleanId(idText: String): String = {
    if (idText.isEmpty) idText
    else if (idText.head == '`') idText
    else backSlashReplaceRegex.replaceAllIn(idText, "$1")
  }
}

trait ScDocRefStrictMemberIdQuery extends ScDocRefQuery with ScDocResolvableCodeReference {
  def memberId: Option[String] =
    findLastChildByTypeScala[PsiElement](ScalaTokenTypes.tIDENTIFIER)
      .map(_.getText)
      .map(ScDocRefQuery.cleanId)
}

trait ScDocRefQuerySegment extends ScDocRefQuery with ScDocResolvableCodeReference {
  override def qualifier: Option[ScDocRefQuerySegment] = findChild[ScDocRefQuerySegment]
  override def pathQualifier: Option[ScDocRefQuery] = findChild[ScDocRefQuery]
}

trait ScDocRefThisQuery extends ScDocRefQuery {
  def thisToken: PsiElement = this.getFirstChild

  def resolveThis(): Option[ScNamedElement with ScDocCommentOwner]

  override def resolve(): ScNamedElement with ScDocCommentOwner
}

trait ScPackageQuery extends ScDocRefQuery {
  def packageToken: PsiElement = this.getFirstChild

  def resolvePackage(): Option[ScPackage]

  override def resolve(): ScPackage
}
