package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

trait ScEnum extends ScClass {
  def cases: Seq[ScEnumCase]
}

object ScEnum {
  /**
   * Checks whether the given enum definition is compatible with a Java enum.
   *
   * A Scala enum is Java-compatible when it extends `java.lang.Enum` parameterized with itself.
   *
   * @param enumDefinition the enum definition to check
   * @return `true` if the enum definition is Java-compatible, `false` otherwise
   */
  def isJavaCompatible(enumDefinition: ScEnum): Boolean = {
    import enumDefinition.projectContext

    def isJavaEnumSelfType(parentType: ScType): Boolean = parentType match {
      case ParameterizedType(designator, Seq(argument)) =>
        designator.extractClass.exists(_.qualifiedName == CommonClassNames.JAVA_LANG_ENUM) &&
          argument.extractClass.contains(enumDefinition)
      case _ => false
    }

    enumDefinition.extendsBlock.templateParents.toSeq
      .flatMap(_.typeElements)
      .flatMap(_.`type`().toOption)
      .exists(isJavaEnumSelfType)
  }
}
