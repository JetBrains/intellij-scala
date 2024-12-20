package org.jetbrains.plugins.scala.gotoclass

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType
import com.intellij.ide.actions.searcheverywhere.{PSIPresentationBgRendererWrapper, SEResultsEqualityProvider, SearchEverywhereFoundElementInfo}
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.{ApiStatus, Nullable}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

import java.util
import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * @see Java implementation [[com.intellij.ide.JavaClassAndFileEqualityProvider]]
 * @see Kotlin implementation `org.jetbrains.kotlin.idea.searcheverywhere.KtSearchEverywhereEqualityProvider`
 */
@ApiStatus.Internal
final class ScalaSearchEverywhereEqualityProvider extends SEResultsEqualityProvider {

  override def compareItems(
    newItem: SearchEverywhereFoundElementInfo,
    alreadyFoundItems: util.List[_ <: SearchEverywhereFoundElementInfo]
  ): SEEqualElementsActionType = {
    val newPsiElement = toPsi(newItem)
    val action: SEEqualElementsActionType =
      alreadyFoundItems.iterator().asScala
        .map(getActionType(newPsiElement, _))
        .filterNot(_ == null)
        .nextOption()
        .getOrElse(SEEqualElementsActionType.DoNothing.INSTANCE)
    action
  }

  /**
   * @return null if we don't want to choose any action for this pair
   */
  @Nullable
  private def getActionType(
    newPsiElement: PsiElement,
    alreadyFoundItem: SearchEverywhereFoundElementInfo
  ): SEEqualElementsActionType = {
    val alreadyFoundPsiElement = toPsi(alreadyFoundItem)
    alreadyFoundPsiElement match {
      case null =>
        null
      case wrapper: PsiClassWrapper if wrapper.definition eq newPsiElement =>
        new SEEqualElementsActionType.Replace(alreadyFoundItem)
      case _ =>
        newPsiElement match {
          case wrapper: PsiClassWrapper if wrapper.definition eq alreadyFoundPsiElement =>
            SEEqualElementsActionType.Skip.INSTANCE
          case _ =>
            null
        }
    }
  }

  private def toPsi(item: SearchEverywhereFoundElementInfo): PsiElement = {
    //For some reason, sometimes the found elements are wrapped into SearchEverywhereFoundElementInfoWithMl.
    //In this case PSIPresentationBgRendererWrapper.toPsi returns null and we need to unwrap it one more time
    val psi: PsiElement = PSIPresentationBgRendererWrapper.toPsi(item)
    if (psi != null)
      psi
    else
      PSIPresentationBgRendererWrapper.toPsi(item.getElement)
  }
}
