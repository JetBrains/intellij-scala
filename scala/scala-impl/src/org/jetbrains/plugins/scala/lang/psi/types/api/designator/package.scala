package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType

package object designator {
  implicit class ScDesignatorTypeOps(val des: DesignatorOwner) extends AnyVal {
    def polyTypeOption: Option[ScTypePolymorphicType] = {
      val typeParams = des.extractDesignated(false) match {
        case Some(alias: ScTypeAlias)  => alias.typeParameters.map(TypeParameter(_))
        case Some(tparam: ScTypeParam) => tparam.typeParameters.map(TypeParameter(_))
        case Some(cls: PsiClass)       => cls.getTypeParameters.map(TypeParameter(_)).toSeq
        case _                         => Seq.empty
      }

      if (typeParams.isEmpty) None
      else {
        val appliedToParams = ScParameterizedType(des, typeParams.map(TypeParameterType(_)))
        ScTypePolymorphicType(appliedToParams, typeParams).toOption
      }
    }
  }
}
