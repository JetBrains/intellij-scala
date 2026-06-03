package org.jetbrains.sbt.project.modifier.location

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.sbt.project.modifier.BuildFileElementType

object EndOfFileLocationProvider extends BuildFileModificationLocationProvider {
  override def findLocationInFile(file: PsiFile, module: com.intellij.openapi.module.Module, elementType: BuildFileElementType,
                                  elementCondition: Option[PsiElement => Boolean]): Option[(PsiElement, Int)] = {
    elementCondition match {
      case Some(_) => None
      case None => Some(file, file.getChildren.length)
    }
  }
}
