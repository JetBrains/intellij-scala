package org.jetbrains.plugins.scala
package codeInsight.template

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.{CodeStyleSettingsManager, JavaCodeStyleSettings, ReferenceAdjuster}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, TypeAdjuster}

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 03/09/14.
 */
class ScalaReferenceAdjuster extends ReferenceAdjuster {
  //todo: expression adjuster
  //todo: process returns element, should return element after replacement
  //todo: support useFqInJavadoc
  //todo: support useFqInCode

  override def process(element: ASTNode, addImports: Boolean, incompleteCode: Boolean, useFqInJavadoc: Boolean,
                       useFqInCode: Boolean): ASTNode = {
    processRange(element, element.getTextRange.getStartOffset, element.getTextRange.getEndOffset, addImports,
      incompleteCode, useFqInJavadoc, useFqInCode)
    element
  }

  override def processRange(element: ASTNode, startOffset: Int, endOffset: Int,
                            useFqInJavadoc: Boolean, useFqInCode: Boolean): Unit = {
    processRange(element, startOffset, endOffset, addImports = true, incompleteCode = false,
      useFqInJavadoc = useFqInJavadoc, useFqInCode = useFqInCode)
  }

  def processRange(element: ASTNode, startOffset: Int, endOffset: Int, addImports: Boolean,
                            incompleteCode: Boolean, useFqInJavadoc: Boolean, useFqInCode: Boolean): Unit = {
    val psi = element.getPsi
    if (!psi.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return
    //do not process other languages
    val buffer = new ArrayBuffer[ScalaPsiElement]()
    val visitor = new ScalaRecursiveElementVisitor {
        override def visitElement(element: ScalaPsiElement): Unit = {
          if (element.getTextRange.getStartOffset >= startOffset && element.getTextRange.getEndOffset <= endOffset) {
            buffer += element
          } else super.visitElement(element)
        }
      }
    psi.accept(visitor)
    TypeAdjuster.adjustFor(buffer, addImports)
  }

  override def processRange(element: ASTNode, startOffset: Int, endOffset: Int, project: Project): Unit = {
    val settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[JavaCodeStyleSettings])
    processRange(element, startOffset, endOffset, settings.useFqNamesInJavadocAlways, settings.USE_FQ_CLASS_NAMES)
  }

  override def process(element: ASTNode, addImports: Boolean, incompleteCode: Boolean, project: Project): ASTNode = {
    val settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(classOf[JavaCodeStyleSettings])
    process(element, addImports, incompleteCode, settings.useFqNamesInJavadocAlways, settings.USE_FQ_CLASS_NAMES)
  }
}
