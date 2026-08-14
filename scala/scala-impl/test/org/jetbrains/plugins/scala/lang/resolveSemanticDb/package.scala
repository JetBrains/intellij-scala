package org.jetbrains.plugins.scala.lang

import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers

import scala.annotation.nowarn

package object resolveSemanticDb {

  implicit class RangeOps(private val range: (TextPos, TextPos)) extends AnyVal {
    def contains(pos: TextPos): Boolean =
      range._1.line <= pos.line && pos.line <= range._2.line &&
        range._1.col <= pos.col && pos.col < range._2.col

    def isEmpty: Boolean =
      range._1 == range._2

    def is(pos: TextPos): Boolean =
      range.isEmpty && range._1 == pos

  }

  case class TextPos(line: Int, col: Int) {
    override def toString: String = s"$line:$col"
  }

  object TextPos {
    def fromZeroBased(line: Int, col: Int): TextPos =
      TextPos(line + 1, col + 1)

    def of(e: PsiElement): TextPos = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation[TextPos] { () =>
      at(e.getTextOffset, e.getContainingFile)
    }: @nowarn("cat=deprecation") // TODO: SCL-25196 Rewrite call on a background thread.

    def at(offset: Int, file: PsiFile): TextPos = {
      if (offset < 0) {
        return TextPos(-1, -1)
      }
      val offsetText = file.getText.substring(0, offset)
      val line = offsetText.count(_ == '\n')
      val lastLineStart = offsetText.lastIndexOf('\n') + 1
      val col = offset - lastLineStart
      TextPos.fromZeroBased(line, col)
    }

    implicit val ordering: Ordering[TextPos] = Ordering.by[TextPos, Int](_.line).orElseBy(_.col)
  }

  def isInRefinement(e: PsiElement): Boolean = e.contexts.exists(_.is[ScRefinement])

  /**
   * Whether `e` is a member that only exists as part of a refinement, either because it is written
   * down as one, or because it is a member of an anonymous class, which Scala 3 approximates by a
   * refinement of the parents, see
   * [[org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNewTemplateDefinitionImpl]].
   *
   * Such a member has no symbol of its own in semanticdb.
   */
  def isRefinementMember(e: PsiElement): Boolean =
    isInRefinement(e) || e.parentOfType[ScTemplateDefinition].exists(_.is[ScNewTemplateDefinition])

  /** The members of the parents that `member` overrides. */
  def overriddenMembers(member: PsiNamedElement): Seq[PsiNamedElement] =
    member.parentOfType[ScTemplateDefinition].toSeq.flatMap { clazz =>
      val node = member match {
        case alias: ScTypeAlias => TypeDefinitionMembers.getTypes(clazz).forName(alias.name).findNode(alias)
        case _                  => TypeDefinitionMembers.getSignatures(clazz).forName(member.name).findNode(member)
      }
      node.toSeq.flatMap(_.supers.map(_.info.namedElement))
    }
}
