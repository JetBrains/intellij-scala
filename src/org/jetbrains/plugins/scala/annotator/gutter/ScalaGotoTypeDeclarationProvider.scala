package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.12.2009
 */

class ScalaGotoTypeDeclarationProvider extends TypeDeclarationProvider {
  def getSymbolTypeDeclarations(symbol: PsiElement): Array[PsiElement] = {
    symbol match {
      case typed: ScTypedDefinition => {
        val res = typed.getType(TypingContext.empty)
        def getForType(tp: ScType): Seq[PsiElement] = ScType.extractClass(tp) match {
          case Some(clazz: PsiClass) => return Seq[PsiElement](clazz)
          case _ => Seq.empty
        }
        val tp = res.getOrElse(return null)
        tp match {
          case ScCompoundType(comps, _, _, _) => comps.flatMap(getForType(_)).toArray
          case _ => getForType(tp).toArray
        }
      }
      case _ => null
    }
  }
}