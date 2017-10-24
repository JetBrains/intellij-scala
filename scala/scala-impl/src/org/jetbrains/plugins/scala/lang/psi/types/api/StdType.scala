package org.jetbrains.plugins.scala.lang.psi.types.api

import java.util.concurrent.atomic.AtomicReference

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.psi.CommonClassNames._
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScType, ScTypeExt, ScUndefinedSubstitutor}
import org.jetbrains.plugins.scala.project.ProjectContext

sealed class StdType(val name: String, val tSuper: Option[StdType])
                    (implicit val projectContext: ProjectContext)
  extends ValueType with NamedType {

  val fullName = s"scala.$name"

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitStdType(this)

  /**
    * Return wrapped to option appropriate synthetic class.
    * In dumb mode returns None (or before it ends to register classes).
    *
    * @return If possible class to represent this type.
    */
  def syntheticClass: Option[ScSyntheticClass] = {
    val classes = SyntheticClasses.get(projectContext)
    if (classes.isClassesRegistered) classes.byName(name) else None
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) =
    (`type` match {
      case stdType: StdType => this == stdType
      case _ =>
        `type`.extractClass match {
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
  def unapply(t: StdType): Option[(String, Option[StdType])] = Some((t.name, t.tSuper))
}

sealed class ValType(override val name: String)(implicit projectContext: ProjectContext)
  extends StdType(name, Some(StdTypes.instance.AnyVal)) {

  override def isFinalType = true
}

class StdTypes(implicit private val projectContext: ProjectContext)
  extends AbstractProjectComponent(projectContext) {

  lazy val Any = new StdType("Any", None)

  lazy val AnyRef = new StdType("AnyRef", Some(Any))

  lazy val Null = new StdType("Null", Some(AnyRef)) {
    override def isFinalType = true
  }

  lazy val Nothing = new StdType("Nothing", Some(Any)) {
    override def isFinalType = true
  }

  lazy val Singleton = new StdType("Singleton", Some(AnyRef)) {
    override def isFinalType = true
  }

  lazy val AnyVal = new StdType("AnyVal", Some(Any))

  lazy val Unit = new ValType("Unit")

  lazy val Boolean = new ValType("Boolean")

  lazy val Char = new ValType("Char")

  lazy val Byte = new ValType("Byte")

  lazy val Short = new ValType("Short")

  lazy val Int = new ValType("Int")

  lazy val Long = new ValType("Long")

  lazy val Float = new ValType("Float")

  lazy val Double = new ValType("Double")

  lazy val QualNameToType: Map[String, StdType] = Seq(
    Any, AnyRef, AnyVal,
    Unit,
    Boolean,
    Byte, Short, Int, Long,
    Char,
    Float, Double,
    Null, Nothing,
    Singleton
  ).map(st => (st.fullName, st)).toMap

  lazy val fqnBoxedToScType = Map(
    JAVA_LANG_BOOLEAN -> Boolean,
    JAVA_LANG_BYTE -> Byte,
    JAVA_LANG_CHARACTER -> Char,
    JAVA_LANG_SHORT -> Short,
    JAVA_LANG_INTEGER -> Int,
    JAVA_LANG_LONG -> Long,
    JAVA_LANG_FLOAT -> Float,
    JAVA_LANG_DOUBLE -> Double
  )

  override def disposeComponent(): Unit = {
    StdTypes.current.compareAndSet(this, null)
  }
}

object StdTypes {
  private val current = new AtomicReference[StdTypes]()

  def instance(implicit pc: ProjectContext): StdTypes = {
    val last = current.get()
    if (last != null && (last.projectContext == pc)) last
    else {
      val fromContainer = pc.getComponent(classOf[StdTypes])
      current.compareAndSet(last, fromContainer)
      fromContainer
    }
  }
}