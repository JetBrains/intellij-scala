package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor}

/**
  * Pavel Fatin
  */
abstract class AbstractInspection(id: String, name: String) extends LocalInspectionTool {

  protected def this() =
    this(AbstractInspection.formatId(getClass), AbstractInspection.formatName(getClass))

  protected def this(name: String) =
    this(AbstractInspection.formatId(getClass), name)

  protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any]

  override def getDisplayName: String = name

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    new AbstractInspection.VisitorWrapper(actionFor(holder))
}

object AbstractInspection {

  private[this] val CapitalLetterPattern = "(?<!=.)\\p{Lu}".r

  private def formatId(clazz: Class[_]): String =
    clazz.getSimpleName.stripSuffix("Inspection")

  private def formatName(clazz: Class[_]): String = {
    val id = formatId(clazz)
    CapitalLetterPattern.replaceAllIn(id, it => s" ${it.group(0).toLowerCase}")
  }

  private class VisitorWrapper(action: PartialFunction[PsiElement, Any]) extends PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit =
      if (action.isDefinedAt(element)) action(element)
  }

}