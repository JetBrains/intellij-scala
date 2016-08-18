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

package org.jetbrains.plugins.scala.lang.psi.api.macros.impl

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.macros.{MacroContext, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, UndefinedType}

/**
 * @author Mikhail.Mutcianko
 *         date 22.12.14
 */

object ShapelessForProduct extends ScalaMacroTypeable {

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    implicit val typeSystem = macros.typeSystem
    if (context.expectedType.isEmpty) return None
    val manager = ScalaPsiManager.instance(context.place.getProject)
    val clazz = manager.getCachedClass("shapeless.Generic", context.place.getResolveScope, ClassCategory.TYPE)
    clazz match {
      case c: ScTypeDefinition =>
        val tpt = c.typeParameters
        if (tpt.isEmpty) return None
        val undef = UndefinedType(TypeParameterType(tpt.head))
        val genericType = ScParameterizedType(ScDesignatorType(c), Seq(undef))
        val (res, undefSubst) = context.expectedType.get.conforms(genericType, new ScUndefinedSubstitutor())
        if (!res) return None
        undefSubst.getSubstitutor match {
          case Some(subst) =>
            val productLikeType = subst.subst(undef)
            val parts = ScPattern.extractProductParts(productLikeType, context.place)
            if (parts.isEmpty) return None
            val coloncolon = manager.getCachedClass("shapeless.::", context.place.getResolveScope, ClassCategory.TYPE)
              .getOrElse(return None)
            val hnil = manager.getCachedClass("shapeless.HNil", context.place.getResolveScope, ClassCategory.TYPE)
              .getOrElse(return None)
            val repr = parts.foldRight(ScDesignatorType(hnil): ScType) {
              case (part, resultType) => ScParameterizedType(ScDesignatorType(coloncolon), Seq(part, resultType))
            }
            ScalaPsiUtil.getCompanionModule(c) match {
              case Some(obj: ScObject) =>
                val elem = obj.members.find {
                  case a: ScTypeAlias if a.name == "Aux" => true
                  case _ => false
                }
                if (elem.isEmpty) return None
                Some(ScParameterizedType(ScProjectionType(ScDesignatorType(obj), elem.get.asInstanceOf[PsiNamedElement],
                  superReference = false), Seq(productLikeType, repr)))
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }
}
