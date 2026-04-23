package org.jetbrains.plugins.scala.structureView

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.structureView.element.Element

final class ScalaNavBarModelExtension extends StructureAwareNavBarModelExtension {
  override def getLanguage: Language = ScalaLanguage.INSTANCE

  // include Scala 3, Worksheet, etc.
  override def isAcceptableLanguage(element: PsiElement): Boolean =
    element != null && element.getLanguage.isKindOf(getLanguage)

  @Nullable
  override def getPresentableText(item: Any): String = getPresentableText(item, false)

  @Nullable
  override def getPresentableText(item: Any, forPopup: Boolean): String = item match {
    case file: ScalaFile =>
      file.name
    case element: ScalaPsiElement =>
      // TODO(SCL-25346): handle forPopup to show extra information such as method parameters only when it is true
      val structureViewElement = Element.forPsiElement(element)
      val presentableText = structureViewElement.map(_.getPresentableText)
      presentableText.orNull
    case _ =>
      null
  }
}
