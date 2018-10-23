package org.jetbrains.plugins.scala.codeInsight.implicits

import java.lang.reflect.{Field, Modifier}

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.InlayModel
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.implicits.Hint._
import org.jetbrains.plugins.scala.codeInsight.implicits.presentation.Presentation
import org.jetbrains.plugins.scala.extensions.ObjectExt

import scala.collection.JavaConverters._

private case class Hint(presentation: Presentation,
                        element: PsiElement,
                        suffix: Boolean) {

  def addTo(model: InlayModel): Option[Inlay] = {
    val offset = if (suffix) element.getTextRange.getEndOffset else element.getTextRange.getStartOffset

    val existingInlays = model.getInlineElementsInRange(offset, offset).asScala.filter(isImplicitHint)

    val inlay = {
      val renderer = new PresentationRenderer(presentation) // TODO
      if (ImplicitHints.expanded) {
        presentation.expand(ImplicitHints.ExpansionThreshold)
      }
      //gives more natural behaviour
      val relatesToPrecedingText = false
      model.addInlineElement(offset, relatesToPrecedingText, renderer)
    }

    // TODO merge to sequence
    if (existingInlays.nonEmpty) {
      // InlayImpl.myOriginalOffset is used solely for inlay sorting by InlayModelImpl
      // TODO Support user-defined order of inlays with the same offset in IDEA API
      Hint.myOriginalOffsetField.foreach { field =>
        val offsets = existingInlays.map(field.getInt)
        field.setInt(inlay, if (suffix) offsets.max + 1 else offsets.min - 1)
      }
    }

    Option(inlay)
  }

  // We want auto-generate apply() and copy() methods, but reference-based equality
  override def equals(obj: scala.Any): Boolean = obj.asOptionOf[AnyRef].exists(eq)
}

private object Hint {
  def isImplicitHint(inlay: Inlay): Boolean = inlay.getRenderer.isInstanceOf[PresentationRenderer]

  // TODO
  private val myOriginalOffsetField: Option[Field] = try {
    val inlayImplClass = Class.forName("com.intellij.openapi.editor.impl.InlayImpl")
    val myOriginalOffsetField = inlayImplClass.getDeclaredField("myOriginalOffset")
    myOriginalOffsetField.setAccessible(true)

    val modifiersField = classOf[Field].getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(myOriginalOffsetField, myOriginalOffsetField.getModifiers & ~Modifier.FINAL)

    Some(myOriginalOffsetField)
  } catch {
    case _: Throwable =>
      if (ApplicationManager.getApplication.isInternal) {
        val log = Logger.getInstance(classOf[Hint])
        log.warn("No myOriginalOffset field in com.intellij.openapi.editor.impl.InlayImpl")
      }
      None
  }
}