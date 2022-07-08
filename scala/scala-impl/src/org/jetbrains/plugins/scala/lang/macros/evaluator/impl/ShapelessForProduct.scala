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

package org.jetbrains.plugins.scala.lang
package macros
package evaluator
package impl

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ProjectContext

object ShapelessForProduct extends ScalaMacroTypeable {

  override val boundMacro: Seq[MacroImpl] =
    MacroImpl("product", "shapeless.Generic") ::
      MacroImpl("apply", "shapeless.LowPriorityGeneric") ::
      Nil

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    val place = context.place
    implicit val elementScope: ElementScope = place.elementScope

    for {
      expectedType <- context.expectedType
      genericClass <- findShapelessClass("Generic").collect {
        case scalaClass: ScTypeDefinition => scalaClass
      }

      nilTrait <- findShapelessClass("HNil")
      nilType = ScDesignatorType(nilTrait)

      consClass <- findShapelessClass("::")
      consType = ScDesignatorType(consClass)

      genericObject <- ScalaPsiUtil.getCompanionModule(genericClass).filterByType[ScObject]

      auxAlias <- genericObject.membersWithSynthetic.findFirstBy[ScTypeAlias](_.name == "Aux")

      productLikeType <- productLikeType(genericClass, expectedType)

      repr = reprType(productLikeType, place)(nilType, consType)
      if !repr.isInstanceOf[ScDesignatorType]

      projectionType = ScProjectionType(ScDesignatorType(genericObject), auxAlias)
    } yield ScParameterizedType(projectionType, Seq(productLikeType, repr))
  }

  private[this] def findShapelessClass(name: String)
                                      (implicit scope: ElementScope) =
    scope.getCachedClass(s"shapeless.$name")

  private[this] def reprType(`type`: ScType, place: PsiElement)
                            (nilType: ScType, consType: ScType) =
    ScPattern.extractPossibleProductParts(`type`, place).foldRight(nilType) {
      case (part, resultType) => ScParameterizedType(consType, Seq(part, resultType))
    }

  private[this] def productLikeType(genericClass: ScTypeDefinition,
                                    expectedType: ScType)
                                   (implicit context: ProjectContext) = for {
    parameter <- genericClass.typeParameters.headOption
    undefinedType = UndefinedType(parameter)
    genericType = ScParameterizedType(ScDesignatorType(genericClass), Seq(undefinedType))

    substitutor <- expectedType.conformanceSubstitutor(genericType)
  } yield substitutor(undefinedType)
}
