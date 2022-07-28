package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, ScTypeExt}

class ScalaGotoTypeDeclarationProvider extends TypeDeclarationProvider {
  override def getSymbolTypeDeclarations(symbol: PsiElement): Array[PsiElement] = {
    symbol match {
      case typed: ScTypedDefinition =>
        val res = typed.`type`()
        def getForType(tp: ScType): Seq[PsiElement] = tp.extractClass match {
          case Some(clazz: PsiClass) => Seq[PsiElement](clazz)
          case _ => Seq.empty
        }
        val tp = res.getOrElse(return null)
        tp match {
          case ScCompoundType(comps, _, _) => comps.flatMap(getForType).toArray
          case _ => getForType(tp).toArray
        }
      case _ => null
    }
  }
}