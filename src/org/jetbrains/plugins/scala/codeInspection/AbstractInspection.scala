package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.VisitorWrapper

/**
 * Pavel Fatin
 */
abstract class AbstractInspection(id: String, name: String) extends LocalInspectionTool {
  def this() {
    this(AbstractInspection.formatId(getClass), AbstractInspection.formatName(getClass))
  }

  def this(name: String) {
    this(AbstractInspection.formatId(getClass), name)
  }

  override def getDisplayName: String = name

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper(actionFor(holder))

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any]
}

object AbstractInspection {
  private val CapitalLetterPattern = "(?<!=.)\\p{Lu}".r

  def formatId(aClass: Class[_]) = {
    aClass.getSimpleName.stripSuffix("Inspection")
  }

  def formatName(aClass: Class[_]) = {
    val id = formatId(aClass)
    CapitalLetterPattern.replaceAllIn(id, it => s" ${it.group(0).toLowerCase}")
  }
}