package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScDesignatorType, ScType, ScTypeExt, ScUndefinedSubstitutor}

abstract class StdType(val name: String, val tSuper: Option[StdType]) extends ValueType with NamedType {
  override def visitType(visitor: TypeVisitor) = visitor.visitStdType(this)

  /**
   * Return wrapped to option appropriate synthetic class.
   * In dumb mode returns None (or before it ends to register classes).
    *
    * @param project in which project to find this class
   * @return If possible class to represent this type.
   */
  def asClass(project: Project): Option[ScSyntheticClass] = {
    val classes = SyntheticClasses.get(project)
    if (classes.isClassesRegistered) classes.byName(name) else None
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    (this, `type`) match {
      case (l: StdType, _: StdType) => (l == `type`, substitutor)
      case (AnyRef, _) =>
        `type`.extractClass() match {
          case Some(clazz) if clazz.qualifiedName == "java.lang.Object" => (true, substitutor)
          case _ => (false, substitutor)
        }
      case (_, _) =>
        `type`.extractClass() match {
          case Some(o: ScObject) => (false, substitutor)
          case Some(clazz) if clazz.qualifiedName == "scala." + name => (true, substitutor)
          case _ => (false, substitutor)
        }
    }
  }
}

object StdType {
  val QualNameToType = Map(
    "scala.Any" -> Any,
    "scala.AnyRef" -> AnyRef,
    "scala.AnyVal" -> AnyVal,
    "scala.Unit" -> Unit,
    "scala.Boolean" -> Boolean,
    "scala.Byte" -> Byte,
    "scala.Short" -> Short,
    "scala.Char" -> Char,
    "scala.Int" -> Int,
    "scala.Long" -> Long,
    "scala.Double" -> Double,
    "scala.Float" -> Float,
    "scala.Null" -> Null,
    "scala.Nothing" -> Nothing,
    "scala.Singleton" -> Singleton
  )

  import com.intellij.psi.CommonClassNames._
  val fqnBoxedToScType = Map(
    JAVA_LANG_BOOLEAN -> Boolean,
    JAVA_LANG_BYTE -> Byte,
    JAVA_LANG_CHARACTER -> Char,
    JAVA_LANG_SHORT -> Short,
    JAVA_LANG_INTEGER -> Int,
    JAVA_LANG_LONG -> Long,
    JAVA_LANG_FLOAT -> Float,
    JAVA_LANG_DOUBLE -> Double
  )

  def unboxedType(tp: ScType): ScType = {
    val name = tp.canonicalText.stripPrefix("_root_.")
    if (fqnBoxedToScType.contains(name)) fqnBoxedToScType(name)
    else tp
  }

  def unapply(tp: StdType): Option[(String, Option[StdType])] = Some(tp.name, tp.tSuper)
}

trait ValueType extends ScType {
  def isValue = true

  def inferValueType: ValueType = this
}

case object Any extends StdType("Any", None)

case object Null extends StdType("Null", Some(AnyRef)) {
  override def isFinalType = true
}

case object AnyRef extends StdType("AnyRef", Some(Any))

case object Nothing extends StdType("Nothing", Some(Any)) {
  override def isFinalType = true
}

case object Singleton extends StdType("Singleton", Some(AnyRef)) {
  override def isFinalType = true
}

case object AnyVal extends StdType("AnyVal", Some(Any))

abstract class ValType(override val name: String) extends StdType(name, Some(AnyVal)) {
  def apply(element: PsiElement): ScType = apply(element.getManager, element.getResolveScope)

  def apply(manager: PsiManager, scope: GlobalSearchScope): ScType =
    ScalaPsiManager.instance(manager.getProject).getCachedClass(scope, "scala." + name)
      .map(ScDesignatorType(_))
      .getOrElse(this)

  override def isFinalType = true
}

case object Unit extends ValType("Unit")

case object Boolean extends ValType("Boolean")

case object Char extends ValType("Char")

case object Int extends ValType("Int")

case object Long extends ValType("Long")

case object Float extends ValType("Float")

case object Double extends ValType("Double")

case object Byte extends ValType("Byte")

case object Short extends ValType("Short")
