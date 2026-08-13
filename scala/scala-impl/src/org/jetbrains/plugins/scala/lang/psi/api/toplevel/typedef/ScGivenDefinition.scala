package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
 * Structural given definition, for example:
 * {{{
 *   given myGiven: MyTrait with {
 *     def foo: Int = ???
 *   }
 *
 *   given OrdList[T](using ord: Ord[T]): Ord[List[T]] with
 *      def compare(xs: List[T], ys: List[T]) =???
 * }}}
 *
 * The key part is that there is no `=` sign as opposed to [[ScGivenAliasDefinition]]
 */
trait ScGivenDefinition extends ScTypeDefinition with ScGiven {
  def desugaredDefinitions: Seq[ScMember]

  def givenType(): TypeResult

  override def getNavigationElement: PsiElement =
    if (nameElement.isDefined) super.getNavigationElement else extendsBlock
}

object ScGivenDefinition {
  object DesugaredTypeDefinition {
    def unapply(tdef: ScTypeDefinition): Option[ScGivenDefinition] =
      Option(tdef.originalGivenElement)
  }

  /** Any of the definitions a structural given is desugared to, see [[ScGivenDefinition.desugaredDefinitions]] */
  object DesugaredDefinition {
    def unapply(member: ScMember): Option[ScGivenDefinition] =
      Option(member.originalGivenElement)
  }
}
