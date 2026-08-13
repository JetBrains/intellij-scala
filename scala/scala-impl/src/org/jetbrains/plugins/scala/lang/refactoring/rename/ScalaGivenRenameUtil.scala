package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScGivenPattern
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven

import java.util
import scala.jdk.CollectionConverters._

/**
 * An anonymous given is named after its type, e.g. the given in `given Seq[Foo] = ???` is called `given_Seq_Foo`
 * (see [[ScalaPsiUtil.generateGivenName]]).
 *
 * So whenever such a type is renamed, all usages of the synthetic name have to be renamed as well (SCL-23444): {{{
 *   given Seq[Foo] = ???           given Seq[Bar] = ???
 *   summon[Seq[Foo]]        -->    summon[Seq[Bar]]
 *   given_Seq_Foo                  given_Seq_Bar
 * }}}
 *
 * Note that the given definition itself is not renamed,
 * its (synthetic) name automatically follows the renamed type.
 */
object ScalaGivenRenameUtil {

  /**
   * @param references references to the element that is about to be renamed
   * @return references to synthetic given names that have to be renamed together with `renamedElement`
   */
  def syntheticGivenNameReferences(
    renamedElement: PsiElement,
    references: util.Collection[PsiReference]
  ): Seq[PsiReference] = {
    val anonymousGivens = references.asScala.iterator
      .flatMap(enclosingAnonymousGivens)
      .distinct
      .filter(nameDependsOn(_, renamedElement))
      .toSeq

    anonymousGivens.flatMap { anonymousGiven =>
      ReferencesSearch.search(anonymousGiven, anonymousGiven.getUseScope).findAll().asScala.toSeq
        .map(new SyntheticGivenNameReference(_, anonymousGiven, renamedElement))
    }
  }

  private def enclosingAnonymousGivens(reference: PsiReference): Iterator[ScNamedElement] =
    reference.getElement.withParentsInFile.collect {
      case given: ScGiven if given.nameElement.isEmpty => given
      case pattern: ScGivenPattern                     => pattern
    }

  /**
   * True if the name of `anonymousGiven` is derived from the name of `renamedElement`,
   * which is the case when pretending to rename it to different names results in different given names.
   *
   * Note that not every reference in a type contributes to the generated name,
   * e.g. the given in `given Seq[Seq[Foo]] = ???` is called `given_Seq_Seq`, no matter how `Foo` is called.
   */
  private def nameDependsOn(anonymousGiven: ScNamedElement, renamedElement: PsiElement): Boolean =
    generatedName(anonymousGiven, renamedElement, "A") != generatedName(anonymousGiven, renamedElement, "B")

  /** The name `anonymousGiven` will have when `renamedElement` is renamed to `newNameOfRenamedElement` */
  private def generatedName(
    anonymousGiven: ScNamedElement,
    renamedElement: PsiElement,
    newNameOfRenamedElement: String
  ): String = {
    def nameOf(reference: ScReference): String =
      //aliased references are not renamed, so they keep contributing their old name
      if (!ScalaRenameUtil.isAliased(reference) && reference.isReferenceTo(renamedElement)) newNameOfRenamedElement
      else reference.refName

    ScalaPsiUtil.generateGivenName(ScalaPsiUtil.givenNameTypeElements(anonymousGiven), nameOf _)
  }

  /**
   * Renames a usage of the synthetic name of `anonymousGiven`
   * to the name the given will have after `renamedElement` has been renamed.
   *
   * The new name of the given cannot be computed upfront, because it depends on
   * the new name of `renamedElement`, which is only known when the rename is actually performed.
   *
   * It doesn't matter whether the type of the given has already been renamed at that point:
   * its references then simply contribute the new name themselves.
   */
  private class SyntheticGivenNameReference(
    delegate: PsiReference,
    anonymousGiven: ScNamedElement,
    renamedElement: PsiElement
  ) extends PsiReference {

    override def handleElementRename(newElementName: String): PsiElement =
      delegate.handleElementRename(generatedName(anonymousGiven, renamedElement, newElementName))

    override def getElement: PsiElement = delegate.getElement

    override def getRangeInElement: TextRange = delegate.getRangeInElement

    override def resolve(): PsiElement = delegate.resolve()

    override def getCanonicalText: String = delegate.getCanonicalText

    override def bindToElement(element: PsiElement): PsiElement = delegate.bindToElement(element)

    override def isReferenceTo(element: PsiElement): Boolean = delegate.isReferenceTo(element)

    override def getVariants: Array[AnyRef] = delegate.getVariants

    override def isSoft: Boolean = delegate.isSoft
  }
}
