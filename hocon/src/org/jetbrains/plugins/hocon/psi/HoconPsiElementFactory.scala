package org.jetbrains.plugins.hocon.psi

import com.intellij.psi.{PsiFileFactory, PsiManager}
import org.jetbrains.plugins.hocon.lang.HoconFileType

import scala.reflect.ClassTag

object HoconPsiElementFactory {
  private val Dummy = "dummy."

  private def createElement[T <: HoconPsiElement : ClassTag](manager: PsiManager, text: String, offset: Int): T = {
    val element = PsiFileFactory.getInstance(manager.getProject)
      .createFileFromText(Dummy + HoconFileType.DefaultExtension, HoconFileType, text).findElementAt(offset)
    Iterator.iterate(element)(_.getParent).collectFirst({ case t: T => t }).get
  }

  def createStringValue(contents: String, manager: PsiManager): HStringValue =
    createElement[HStringValue](manager, s"k = $contents", 4)

  def createKeyPart(contents: String, manager: PsiManager): HKeyPart =
    createElement[HKeyPart](manager, s"$contents = null", 0)

  def createIncludeTarget(contents: String, manager: PsiManager): HIncludeTarget =
    createElement[HIncludeTarget](manager, s"include $contents", 8)

  def createKey(contents: String, manager: PsiManager): HKey =
    createElement[HKey](manager, s"$contents = null", 0)
}
