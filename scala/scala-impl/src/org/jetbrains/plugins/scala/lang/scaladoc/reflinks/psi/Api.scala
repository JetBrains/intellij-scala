package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuerySegment.{IdSelector, PackageSelector, Selector, ThisSelector}


sealed trait ScDocRefElement extends ScalaPsiElement {

}

sealed trait ScDocRefQuery extends ScDocRefElement {

}

object ScDocRefQuery {
  private val backSlashReplaceRegex = raw"\\(?!\\)".r
  def cleanId(idText: String): String = {
    if (idText.isEmpty) idText
    else if (idText.head == '`') idText.stripPrefix("`").stripSuffix("`")
    else backSlashReplaceRegex.replaceAllIn(idText, "")
  }
}

trait ScDocRefStrictMemberIdQuery extends ScDocRefQuery {
  def memberId: Option[String] =
    findLastChildByTypeScala[PsiElement](ScalaTokenTypes.tIDENTIFIER)
      .map(_.getText)
      .map(ScDocRefQuery.cleanId)
}

trait ScDocRefQuerySegment extends ScDocRefQuery {
  def qualifier: Option[ScDocRefQuerySegment] = findChild[ScDocRefQuerySegment]

  def selector: Option[Selector] = {
    val lastChild = getLastChild
    lastChild.elementType match {
      case ScalaTokenTypes.tIDENTIFIER => Some(IdSelector(ScDocRefQuery.cleanId(lastChild.getText)))
      case ScalaTokenTypes.kTHIS => Some(ThisSelector)
      case ScalaTokenTypes.kPACKAGE => Some(PackageSelector)
      case _ => None
    }
  }
}

object ScDocRefQuerySegment {
  sealed trait Selector {
    def text: String
  }
  case class IdSelector(id: String) extends Selector {
    override def text: String = id
  }
  case object PackageSelector extends Selector {
    override def text: String = "package"
  }
  case object ThisSelector extends Selector {
    override def text: String = "this"
  }
}