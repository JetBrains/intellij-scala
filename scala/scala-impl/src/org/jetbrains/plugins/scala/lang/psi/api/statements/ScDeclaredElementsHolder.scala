package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * Common abstraction for PSI nodes that declare one or more named program elements.
 *
 * The key idea is that one syntactic holder can correspond to several declarations.
 * For example, one `val` statement can introduce multiple bindings.
 *
 * == Direct implementors (with representative examples): ==
 *
 *   - [[ScValueOrVariable]] {{{
 *       val a, b = 1
 *       // declaredElements: Seq(a, b)
 *      }}}
 *
 *   - [[ScFunction]] {{{
 *       def parse(text: String): Int = text.length
 *       // declaredElements: Seq(parse)
 *      }}}
 *
 *   - [[ScExtension]] {{{
 *       extension (i: Int)
 *         def twice: Int = i * 2
 *         def thrice: Int = i * 3
 *       // declaredElements: Seq(twice, thrice)
 *      }}}
 *
 *   - [[ScEnumCases]] {{{
 *       enum Color:
 *         case Red, Green
 *       // declaredElements: Seq(Red, Green)
 *      }}}
 *
 *   - [[toplevel.typedef.ScObject]] {{{
 *       object Service
 *       // declaredElements: Seq(Service)
 *      }}}
 *
 *   - [[toplevel.typedef.ScGiven]] {{{
 *       given intOrdering: Ordering[Int] with
 *         override def compare(x: Int, y: Int): Int = x.compare(y)
 *       // declaredElements: Seq(intOrdering)
 *      }}}
 *
 *   - [[toplevel.ScPackaging]] {{{
 *       package foo.bar
 *       // declaredElements: Seq(package foo)
 *      }}}
 *
 * == Note on enum cases: ==
 * [[ScEnumCase]] itself is a type definition and does not extend this trait directly.
 * The holder is [[ScEnumCases]], because a single `case` clause may declare multiple enum cases.
 *
 * == Why [[toplevel.typedef.ScObject]] immplements this ==
 * Object declarations can participate in value-like member overriding and navigation scenarios where
 * a declaration-holder contract is needed
 * (historically added in SCL-2167 to support "Go To Super" from object declarations to parent members))
 *
 * ==Why [[toplevel.typedef.ScTemplateDefinition]] does not extend this trait: ==
 * They model type definitions (class/trait/object/enum) and inheritance structure, not a declaration list API.
 * Their members are exposed via dedicated APIs like `members`, `properties`, `functions`, and `typeDefinitions`.
 * Only nodes that naturally act as declaration holders of named elements implement this trait.
 */
trait ScDeclaredElementsHolder extends ScalaPsiElement {
  def declaredElements : Seq[PsiNamedElement]

  def declaredNames: Seq[String] = declaredElements.map(_.name)

  /**
   * @return array for Java compatibility [[org.jetbrains.plugins.scala.gotoclass.ScalaGoToSymbolContributor]]
   */
  def declaredElementsArray : Array[PsiNamedElement] = declaredElements.toArray
}
