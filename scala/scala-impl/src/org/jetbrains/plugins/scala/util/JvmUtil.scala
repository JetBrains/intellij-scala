package org.jetbrains.plugins.scala.util

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType}
import org.jetbrains.plugins.scala.project.ProjectExt

private[scala] object JvmUtil {
  def getJVMStringForType(tp: ScType, isParam: Boolean = true): String = {
    val stdTypes = tp.getProject.stdTypes
    import stdTypes._

    tp match {
      case AnyRef => "Ljava/lang/Object;"
      case Any => "Ljava/lang/Object;"
      case Singleton => "Ljava/lang/Object;"
      case Null => "Lscala/Null$;"
      case Nothing => "Lscala/Nothing$;"
      case Boolean => "Z"
      case Byte => "B"
      case Char => "C"
      case Short => "S"
      case Int => "I"
      case Long => "J"
      case Float => "F"
      case Double => "D"
      case Unit if isParam => "Lscala/runtime/BoxedUnit;"
      case Unit => "V"
      case JavaArrayType(arg) => "[" + getJVMStringForType(arg)
      case ParameterizedType(ScDesignatorType(clazz: PsiClass), Seq(arg))
        if clazz.qualifiedName == "scala.Array" => "[" + getJVMStringForType(arg)
      case _ =>
        tp.extractClass match {
          case Some(obj: ScObject) => "L" + obj.getQualifiedNameForDebugger.replace('.', '/') + "$;"
          case Some(obj: ScTypeDefinition) => "L" + obj.getQualifiedNameForDebugger.replace('.', '/') + ";"
          case Some(clazz) => "L" + clazz.qualifiedName.replace('.', '/') + ";"
          case _ => "Ljava/lang/Object;"
        }
    }
  }
}
