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

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

case class MacroInvocationContext(call: MethodInvocation, resolveResult: ScalaResolveResult)

case class MacroContext(place: PsiElement, expectedType: Option[ScType])

case class MacroImpl(name: String, clazz: String)

trait ScalaMacroBound {
  val boundMacro: Seq[MacroImpl]
}

trait ScalaMacroTypeable extends ScalaMacroBound {
  def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType]
}

trait ScalaMacroExpandable extends ScalaMacroBound {
  def expandMacro(macros: ScFunction, context: MacroInvocationContext): Option[ScExpression]
}