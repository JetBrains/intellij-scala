/*
 * Copyright 2000-2014 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.macros.evaluator

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.components.libextensions.LibraryExtensionsManager
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.macros.evaluator.impl._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import scala.reflect.ClassTag

class ScalaMacroEvaluator(project: Project) {
  import ScalaMacroEvaluator._

  @Cached(LibraryExtensionsManager.MOD_TRACKER, null)
  private def typingRules:    Map[MacroImpl, ScalaMacroTypeable]    = loadRules(defaultTypeProviders)

  @Cached(LibraryExtensionsManager.MOD_TRACKER, null)
  private def expansionRules: Map[MacroImpl, ScalaMacroExpandable]  = loadRules(defaultExprProviders)

  private def loadRules[T <: ScalaMacroBound](defaults: Seq[T])(implicit tag: ClassTag[T]) : Map[MacroImpl, T] = {
    val external = LibraryExtensionsManager.getInstance(project).getExtensions[T]
    defaults.flatMap(p => p.boundMacro.map(_ -> p)).toMap ++
      external.flatMap(p => p.boundMacro.map(_ -> p)).toMap
  }

  def checkMacro(named: PsiNamedElement, context: MacroContext): Option[ScType] = {
    macroSupport(named, typingRules).flatMap {
      case (m, x) => x.checkMacro(m, context)
    }
  }

  def expandMacro(named: PsiNamedElement, context: MacroInvocationContext): Option[ScExpression] = {
    if (isMacroExpansion(context.call)) return None //avoid recursive macro expansions

    macroSupport(named, expansionRules).flatMap {
      case (m, x) =>
        val expanded = x.expandMacro(m, context)
        expanded.foreach(markMacroExpansion)
        expanded
    }
  }

  private def macroSupport[T](named: PsiNamedElement, map: Map[MacroImpl, T]): Option[(ScFunction, T)] = {
    named match {
      case MacroDef(m) if m.isDefinedInClass =>
        val macroImpl = MacroImpl(m.name, m.containingClass.qualifiedName)
        map.get(macroImpl).map((m, _))
      case _ => None
    }
  }

}

object ScalaMacroEvaluator {
  def getInstance(project: Project): ScalaMacroEvaluator = project.getService(classOf[ScalaMacroEvaluator])

  private val isMacroExpansionKey: Key[AnyRef] = Key.create("macro.original.expression")

  private def isMacroExpansion(expr: ScExpression): Boolean = expr.getUserData(isMacroExpansionKey) != null
  private def markMacroExpansion(expr: ScExpression): Unit  = expr.putUserData(isMacroExpansionKey, this)

  val defaultTypeProviders: Seq[ScalaMacroTypeable] = Seq(
    ShapelessForProduct,
    ShapelessMaterializeGeneric,
    ShapelessDefaultSymbolicLabelling,
    ShapelessMkSelector,
    ShapelessWitnessSelectDynamic
  )

  val defaultExprProviders: Seq[ScalaMacroExpandable] = Seq(
    ShapelessProductArgs
  )
}

