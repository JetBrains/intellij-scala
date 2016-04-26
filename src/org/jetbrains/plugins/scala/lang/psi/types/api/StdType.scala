package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScType, ScTypeExt, ScUndefinedSubstitutor}

abstract class StdType(val name: String, val tSuper: Option[StdType]) extends ValueType with NamedType {
  protected val fullName = s"scala.$name"

  override def visitType(visitor: TypeVisitor) = visitor.visitStdType(this)

  /**
    * Return wrapped to option appropriate synthetic class.
    * In dumb mode returns None (or before it ends to register classes).
    *
    * @param project in which project to find this class
    * @return If possible class to represent this type.
    */
  def asClass(project: Project) = {
    val classes = SyntheticClasses.get(project)
    if (classes.isClassesRegistered) classes.byName(name) else None
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem) = (`type` match {
    case stdType: StdType => this == stdType
    case _ =>
      `type`.extractClass() match {
        case Some(_: ScObject) => false
        case Some(clazz) => clazz.qualifiedName match {
          case this.fullName => true
          case "java.lang.Object" if this == AnyRef => true
          case _ => false
        }
        case _ => false
      }
  }, substitutor)
}

object StdType {
  val QualNameToType = Seq(Any, AnyRef, AnyVal,
    Unit,
    Boolean,
    Byte, Short, Int, Long,
    Char,
    Float, Double,
    Null, Nothing,
    Singleton).map {
    case stdType => (stdType.fullName, stdType)
  }.toMap

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

  def unapply(`type`: StdType): Option[(String, Option[StdType])] = Some(`type`.name, `type`.tSuper)
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
  override def isFinalType = true
}

case object Unit extends ValType("Unit")

case object Boolean extends ValType("Boolean")

case object Char extends ValType("Char")

case object Byte extends ValType("Byte")

case object Short extends ValType("Short")

case object Int extends ValType("Int")

case object Long extends ValType("Long")

case object Float extends ValType("Float")

case object Double extends ValType("Double")
