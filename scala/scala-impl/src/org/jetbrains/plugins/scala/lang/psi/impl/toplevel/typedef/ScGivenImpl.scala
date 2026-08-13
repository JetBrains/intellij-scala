package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createColon, createIdentifier, createWhitespace}

trait ScGivenImpl extends ScGiven {
  override def nameElement: Option[PsiElement] =
    findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)

  /**
   * An anonymous given doesn't have a name that could be changed,
   * its name is derived from its type (see [[ScalaPsiUtil.generateGivenName]]).
   *
   * So instead of renaming it, we make its name explicit: {{{
   *   given Foo = ???     -->   given newName: Foo = ???
   * }}}
   */
  override def setName(name: String): PsiElement = nameElement match {
    case Some(_) => super.setName(name)
    case None =>
      addNameElement(name)
      this
  }

  private def addNameElement(name: String): Unit =
    firstChildAfterGivenKeyword.foreach { anchor =>
      val identifier = createIdentifier(name).getPsi
      if (hasColonBeforeType) anchor.prependSiblings(identifier)
      else                    anchor.prependSiblings(identifier, createColon, createWhitespace)
    }

  /**
   * The element the name has to be inserted in front of,
   * i.e. the first thing that follows the `given` keyword: a type parameter clause, a using clause or the type itself.
   */
  private def firstChildAfterGivenKeyword: Option[PsiElement] =
    findFirstChildByType(ScalaTokenType.GivenKeyword)
      .flatMap(_.nextSiblings.find(child => !child.isWhitespaceOrComment && child.getTextLength > 0))

  /**
   * True for givens that already have a signature colon, i.e. the ones where only the name itself is missing: {{{
   *   given [T]: Seq[T] = ???
   *   given (using i: Int): Foo = ???
   * }}}
   */
  private def hasColonBeforeType: Boolean = {
    val typeStart = ScalaPsiUtil.givenNameTypeElements(this)
      .headOption
      .fold(getTextRange.getEndOffset)(_.getTextRange.getStartOffset)

    val colon = getNode.findChildByType(ScalaTokenTypes.tCOLON)
    colon != null && colon.getStartOffset < typeStart
  }
}
