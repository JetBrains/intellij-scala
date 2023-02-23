package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.editor.{Editor, InlayModel}
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.scala.annotator.hints.Hint

import java.awt.Insets
import java.lang.reflect.{Field, Modifier}
import scala.jdk.CollectionConverters.ListHasAsScala

object ImplicitHint {
  private val EmptyInsets = JBUI.emptyInsets()

  private val ElementKey: Key[PsiElement] = Key.create("SCALA_IMPLICIT_HINT_ELEMENT")

  def elementOf(inlay: Inlay): PsiElement = ElementKey.get(inlay)

  def isImplicitHint(inlay: Inlay): Boolean = inlay.getUserData(ElementKey) != null

  def addTo(hint: Hint, model: InlayModel): Inlay = {
    import hint._

    val offset = if (suffix) element.getTextRange.getEndOffset else element.getTextRange.getStartOffset

    val existingInlays = model.getInlineElementsInRange(offset, offset).asScala.filter(isImplicitHint)

    val inlay = {
      val renderer = new TextPartsHintRenderer(parts, menu) {
        override protected def getMargin(editor: Editor): Insets = margin.getOrElse(EmptyInsets)
      }
      if (ImplicitHints.expanded) {
        renderer.expand()
      }
      model.addInlineElement(offset + offsetDelta, relatesToPrecedingElement, renderer)
    }

    if (existingInlays.nonEmpty) {
      // InlayImpl.myOriginalOffset is used solely for inlay sorting by InlayModelImpl
      // TODO Support user-defined order of inlays with the same offset in IDEA API
      myOriginalOffsetField.foreach { field =>
        val offsets = existingInlays.map(field.getInt)
        field.setInt(inlay, if (suffix) offsets.max + 1 else offsets.min - 1)
      }
    }

    inlay.putUserData(ElementKey, element)
    inlay
  }

  private val myOriginalOffsetField: Option[Field] = try {
    val inlayImplClass = Class.forName("com.intellij.openapi.editor.impl.InlayImpl")
    val myOriginalOffsetField = inlayImplClass.getDeclaredField("myOriginalOffset")
    myOriginalOffsetField.setAccessible(true)

    val modifiersField = classOf[Field].getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(myOriginalOffsetField, myOriginalOffsetField.getModifiers & ~Modifier.FINAL)

    Some(myOriginalOffsetField)
  } catch {
    case c: ControlFlowException => throw c
    case _: Throwable =>
      if (ApplicationManager.getApplication.isInternal) {
        val log = Logger.getInstance(classOf[Hint])
        log.warn("No myOriginalOffset field in com.intellij.openapi.editor.impl.InlayImpl")
      }
      None
  }
}
