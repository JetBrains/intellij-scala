/*
 * Copyright 2000-2014 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.macros.evaluator

import com.intellij.openapi.components._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.macros.evaluator.ScalaMacroEvaluator._
import org.jetbrains.plugins.scala.lang.macros.evaluator.impl._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Mikhail.Mutcianko
 * date 19.12.14
 */

class ScalaMacroEvaluator(project: Project) extends AbstractProjectComponent(project) {

  override def getComponentName = "ScalaMacroEvaluator"

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
      case MacroDef(m) if !m.isLocal =>
        val macroImpl = MacroImpl(m.name, m.containingClass.qualifiedName)
        map.get(macroImpl).map((m, _))
      case _ => None
    }
  }
}

object ScalaMacroEvaluator {
  def getInstance(project: Project): ScalaMacroEvaluator = ServiceManager.getService(project, classOf[ScalaMacroEvaluator])

  private case class MacroImpl(name: String, clazz: String)

  private val typingRules: Map[MacroImpl, ScalaMacroTypeable] = Map(
    MacroImpl("product", "shapeless.Generic")                                     -> ShapelessForProduct,
    MacroImpl("apply", "shapeless.LowPriorityGeneric")                            -> ShapelessForProduct,
    MacroImpl("materialize", "shapeless.Generic")                                 -> ShapelessMaterializeGeneric,
    MacroImpl("mkDefaultSymbolicLabelling", "shapeless.DefaultSymbolicLabelling") -> ShapelessDefaultSymbolicLabelling,
    MacroImpl("mkSelector", "shapeless.ops.record.Selector")                      -> ShapelessMkSelector,
    MacroImpl("selectDynamic", "shapeless.Witness")                               -> ShapelessWitnessSelectDynamic
  )

  private val expansionRules: Map[MacroImpl, ScalaMacroExpandable] = Map(
    MacroImpl("applyDynamic", "shapeless.ProductArgs") -> ShapelessProductArgs
  )

  private val isMacroExpansionKey: Key[AnyRef] = Key.create("macro.original.expression")

  private def isMacroExpansion(expr: ScExpression): Boolean = expr.getUserData(isMacroExpansionKey) != null
  private def markMacroExpansion(expr: ScExpression): Unit = expr.putUserData(isMacroExpansionKey, this)
}

