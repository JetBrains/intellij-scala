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
  /**
   * Synthetic Scala 2-style members used to model this structural given.
   *
   * An object-like given is represented by an implicit object:
   * {{{
   *   given stringProvider: Provider[String] with
   *     def value: String = "value"
   *
   *   // Synthetic definition:
   *   implicit object stringProvider extends Provider[String]
   * }}}
   *
   * A parameterized given is represented by a class and an implicit factory method:
   * {{{
   *   given provider[A]: Provider[A] with
   *     def value: A = ???
   *
   *   // Synthetic definitions:
   *   class provider[A] extends Provider[A]
   *   implicit def provider[A]: provider[A] = ???
   * }}}
   *
   * Every returned member records this given in [[ScMember.originalGivenElement]], allowing
   * consumers to recover the source declaration from its synthetic representation.
   */
  def desugaredDefinitions: Seq[ScMember]

  def givenType(): TypeResult

  override def getNavigationElement: PsiElement =
    if (nameElement.isDefined) super.getNavigationElement else extendsBlock
}

object ScGivenDefinition {
  /**
   * Extracts the source structural given from its synthetic class or object representation.
   *
   * It follows the [[ScMember.originalGivenElement]] association set on the members returned by
   * [[ScGivenDefinition.desugaredDefinitions]]. Unlike [[DesugaredDefinition]], it accepts only
   * synthetic type definitions: the class for a parameterized given or the implicit object for an
   * object-like given. It does not accept the synthetic factory method.
   */
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
