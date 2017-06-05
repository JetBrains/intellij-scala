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
import org.jetbrains.plugins.scala.lang.macros.evaluator.impl.ShapelessForProduct
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScMacroDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Mikhail.Mutcianko
 * date 19.12.14
 */

class ScalaMacroEvaluator(project: Project) extends AbstractProjectComponent(project) with ScalaMacroTypeable {

  override def getComponentName = "ScalaMacroEvaluator"

  lazy val typingRules = Seq(
    MatchRule("product", "shapeless.Generic", ShapelessForProduct),
    MatchRule("apply", "shapeless.LowPriorityGeneric", ShapelessForProduct),
    DefaultRule
  )

  def isMacro(n: PsiNamedElement): Option[ScFunction] = {
    n match {
      case f: ScMacroDefinition => Some(f)
      //todo: fix decompiler to avoid this check:
      case f: ScFunction if f.hasAnnotation("scala.reflect.macros.internal.macroImpl") => Some(f)
      case _ => None
    }
  }

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    typingRules.filter(_.isApplicable(macros)).head.typeable.checkMacro(macros, context)
  }
}

object ScalaMacroEvaluator {
  def getInstance(project: Project): ScalaMacroEvaluator = ServiceManager.getService(project, classOf[ScalaMacroEvaluator])
}

trait MacroRule {
  def isApplicable(fun: ScFunction): Boolean
  def typeable: ScalaMacroTypeable
}

case class MatchRule(name: String, clazz: String, typeable: ScalaMacroTypeable) extends MacroRule {
  def isApplicable(fun: ScFunction): Boolean = {
    fun.name == name && fun.containingClass.qualifiedName == clazz
  }
}

object DefaultRule extends MacroRule {
  override def isApplicable(fun: ScFunction) = true
  override def typeable: ScalaMacroTypeable = ScalaMacroDummyTypeable // TODO: interpreter goes here
}

