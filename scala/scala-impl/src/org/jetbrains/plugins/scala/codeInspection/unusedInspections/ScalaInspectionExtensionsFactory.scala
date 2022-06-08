package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.codeInspection.HTMLComposer
import com.intellij.codeInspection.ex.JavaInspectionExtensionsFactory
import com.intellij.codeInspection.lang.{GlobalInspectionContextExtension, HTMLComposerExtension, InspectionExtensionsFactory, RefManagerExtension}
import com.intellij.codeInspection.reference.RefManager
import com.intellij.psi.PsiElement

import scala.annotation.unused

@unused("registered in scala-plugin-common.xml")
private class ScalaInspectionExtensionsFactory extends InspectionExtensionsFactory {

  val javaInspectionExtensionsFactory = new JavaInspectionExtensionsFactory

  override def createGlobalInspectionContextExtension(): GlobalInspectionContextExtension[_] =
    javaInspectionExtensionsFactory.createGlobalInspectionContextExtension()

  override def createRefManagerExtension(refManager: RefManager): RefManagerExtension[_] =
    new RefScalaManager(refManager)

  override def createHTMLComposerExtension(composer: HTMLComposer): HTMLComposerExtension[_] =
    javaInspectionExtensionsFactory.createHTMLComposerExtension(composer)

  override def isToCheckMember(element: PsiElement, id: String): Boolean =
    javaInspectionExtensionsFactory.isToCheckMember(element, id)

  override def getSuppressedInspectionIdsIn(element: PsiElement): String =
    javaInspectionExtensionsFactory.getSuppressedInspectionIdsIn(element)
}
