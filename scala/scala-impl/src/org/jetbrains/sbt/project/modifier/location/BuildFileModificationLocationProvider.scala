package org.jetbrains.sbt.project.modifier.location

import com.intellij.openapi.module.{Module => IJModule}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.sbt.project.modifier.BuildFileElementType

trait BuildFileModificationLocationProvider {

  def getAddElementLocation(module: IJModule, elementType: BuildFileElementType, buildFile: PsiFile): Option[(PsiElement, Int)] = {
    findLocationInFile(buildFile, module, elementType, None)
  }

  def getModifyOrRemoveElement(module: IJModule, elementType: BuildFileElementType,
                          elementCondition: PsiElement => Boolean, buildFile: PsiFile): Option[PsiElement] = {
    findLocationInFile(buildFile, module, elementType, Some(elementCondition)).
        map{case (parent, index) => parent.getChildren.apply(index)}
  }

  def findLocationInFile(file: PsiFile, module: IJModule, elementType: BuildFileElementType,
                         elementCondition: Option[PsiElement => Boolean]): Option[(PsiElement, Int)]
}
