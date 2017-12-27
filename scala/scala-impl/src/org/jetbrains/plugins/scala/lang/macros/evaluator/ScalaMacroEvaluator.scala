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
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.macros.evaluator.ScalaMacroEvaluator.MacroImpl
import org.jetbrains.plugins.scala.lang.macros.evaluator.impl._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Mikhail.Mutcianko
 * date 19.12.14
 */

class ScalaMacroEvaluator(project: Project) extends AbstractProjectComponent(project) {

  override def getComponentName = "ScalaMacroEvaluator"

  def isMacro(named: PsiNamedElement): Option[ScFunction] = MacroDef.unapply(named)

  def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    if (isMacro(macros).isEmpty) return None

    val macroImpl = MacroImpl(macros.name, macros.containingClass.qualifiedName)

    ScalaMacroEvaluator.typingRules
      .getOrElse(macroImpl, ScalaMacroDummyTypeable)
      .checkMacro(macros, context)
  }
}

object ScalaMacroEvaluator {
  def getInstance(project: Project): ScalaMacroEvaluator = ServiceManager.getService(project, classOf[ScalaMacroEvaluator])

  private case class MacroImpl(name: String, clazz: String)

  private lazy val typingRules: Map[MacroImpl, ScalaMacroTypeable] = Map(
    MacroImpl("product", "shapeless.Generic")                                     -> ShapelessForProduct,
    MacroImpl("apply", "shapeless.LowPriorityGeneric")                            -> ShapelessForProduct,
    MacroImpl("materialize", "shapeless.Generic")                                 -> ShapelessMaterializeGeneric,
    MacroImpl("mkDefaultSymbolicLabelling", "shapeless.DefaultSymbolicLabelling") -> ShapelessDefaultSymbolicLabelling,
    MacroImpl("mkSelector", "shapeless.ops.record.Selector")                      -> ShapelessMkSelector
  )
}

