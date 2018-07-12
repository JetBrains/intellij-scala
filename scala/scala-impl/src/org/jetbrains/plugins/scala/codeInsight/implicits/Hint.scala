package org.jetbrains.plugins.scala.codeInsight.implicits

import java.lang.reflect.{Field, Modifier}

import com.intellij.openapi.editor.{Inlay, InlayModel}
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.implicits.Hint._
import org.jetbrains.plugins.scala.extensions.ObjectExt

import scala.collection.JavaConverters._

private case class Hint(parts: Seq[Text],
                        element: PsiElement,
                        suffix: Boolean,
                        menu: Option[String] = None) {

  def addTo(model: InlayModel): Inlay = {
    val offset = if (suffix) element.getTextRange.getEndOffset else element.getTextRange.getStartOffset

    val existingInlays = model.getInlineElementsInRange(offset, offset).asScala.filter(isImplicitHint)

    val inlay = {
      val renderer = new TextRenderer(parts, menu)
      if (ImplicitHints.expanded) {
        renderer.expand()
      }
      //gives more natural behaviour
      val relatesToPrecedingText = false
      model.addInlineElement(offset, relatesToPrecedingText, renderer)
    }

    if (existingInlays.nonEmpty) {
      // InlayImpl.myOriginalOffset is used solely for inlay sorting by InlayModelImpl
      // TODO Support user-defined order of inlays with the same offset in IDEA API
      Hint.myOriginalOffsetField.foreach { field =>
        val offsets = existingInlays.map(field.getInt)
        field.setInt(inlay, if (suffix) offsets.max + 1 else offsets.min - 1)
      }
    }

    inlay.putUserData(Hint.ElementKey, element)
    inlay
  }

  // We want auto-generate apply() and copy() methods, but reference-based equality
  override def equals(obj: scala.Any): Boolean = obj.asOptionOf[AnyRef].exists(eq)
}

private object Hint {
  private val ElementKey: Key[PsiElement] = Key.create("SCALA_IMPLICIT_HINT_ELEMENT")

  def elementOf(inlay: Inlay): PsiElement = ElementKey.get(inlay)

  def isImplicitHint(inlay: Inlay): Boolean = inlay.getUserData(Hint.ElementKey) != null

  private val myOriginalOffsetField: Option[Field] = try {
    val inlayImplClass = Class.forName("com.intellij.openapi.editor.impl.InlayImpl")
    val myOriginalOffsetField = inlayImplClass.getDeclaredField("myOriginalOffset")
    myOriginalOffsetField.setAccessible(true)

    val modifiersField = classOf[Field].getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(myOriginalOffsetField, myOriginalOffsetField.getModifiers & ~Modifier.FINAL)

    Some(myOriginalOffsetField)
  } catch {
    case _: Throwable => None
  }
}