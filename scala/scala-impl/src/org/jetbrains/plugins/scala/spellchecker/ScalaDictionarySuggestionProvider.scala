package org.jetbrains.plugins.scala.spellchecker

import java.util

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.{NameSuggestionProvider, RenameUtil}
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.quickfixes.DictionarySuggestionProvider
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.jdk.CollectionConverters._

/*
  This class is hacky!

  This NameSuggestionProvider must be executed before the normal DictionarySuggestionProvider
  It very specifically targets ScNamedElements for which getName might not return the actual textual name.

  When the RenameTo quickfix is activated it will call setActive(true) on the first DictionarySuggestionProvider
  it can find. Afterwards some other component will call getSuggestedNames. Here we jump in and look if
  the element is a ScNamedElement. If that is the case we will do the dictionary lookup ourselves.
  Otherwise we will return null so another NameSuggestionProvider
  — preferably and probably the actual DictionarySuggestionProvider — can do the work.

 */
class ScalaDictionarySuggestionProvider extends DictionarySuggestionProvider {

  private var _active = false

  override def setActive(active: Boolean): Unit = {
    _active = active
    super.setActive(active)

    // we also have to relay to the actual DictionarySuggestionProvider
    nextDictionarySuggestionProvider.foreach(_.setActive(active))
  }

  private def nextDictionarySuggestionProvider: Option[DictionarySuggestionProvider] = {
    val providers = NameSuggestionProvider.EP_NAME
      .getExtensionList
      .asScala

    val myIdx = providers.indexOf(this)

    providers
      .iterator
      .drop(myIdx + 1)
      .collectFirst { case provider: DictionarySuggestionProvider => provider }
  }

  override def shouldCheckOthers(): Boolean = true

  override def getSuggestedNames(element: PsiElement, nameSuggestionContext: PsiElement, result: util.Set[String]): SuggestedNameInfo = {
    assert(result != null)

    val name = (element, nameSuggestionContext) match {
      case (named: ScNamedElement, _: ScNamedElement) if _active => named.name
      case _ =>
        // we are only interested in ScNamedElements
        // if it is not a named element let some other NameSuggestionProvider
        // (probably the DictionarySuggestionProvider) do the work
        return null
    }

    val project = element.getProject
    val manager = SpellCheckerManager.getInstance(project)

    manager
      .getSuggestions(name)
      .asScala
      .withFilter(RenameUtil.isValidName(project, element, _))
      .foreach(result.add)

    SuggestedNameInfo.NULL_INFO
  }
}
