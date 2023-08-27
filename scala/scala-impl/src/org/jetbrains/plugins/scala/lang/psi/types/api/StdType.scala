package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames._
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, LeafType, NamedType, ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType.Name
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util.concurrent.atomic.AtomicReference

sealed class StdType(override val name: String, val tSuper: Option[StdType])
                    (implicit override val projectContext: ProjectContext)
  extends ValueType with NamedType with LeafType {

  val fullName = s"scala.$name"

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitStdType(this)

  /**
    * Return wrapped to option appropriate synthetic class.
    * In dumb mode returns None (or before it ends to register classes).
    *
    * @return If possible class to represent this type.
    */
  def syntheticClass: Option[PsiClass] = {
    val classes = SyntheticClasses.get(projectContext)
    if (classes.isClassesRegistered) classes.byName(name) else None
  }

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    val success = `type` match {
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
    }
    if (success) constraints
    else ConstraintsResult.Left
  }
}

object StdType {
  def unapply(t: StdType): Option[(String, Option[StdType])] = Some((t.name, t.tSuper))

  object Name {
    val Any       = "Any"
    val AnyRef    = "AnyRef"
    val AnyKind   = "AnyKind"
    val Null      = "Null"
    val Nothing   = "Nothing"
    val Singleton = "Singleton"
    val AnyVal    = "AnyVal"
    val Unit      = "Unit"
    val Boolean   = "Boolean"
    val Char      = "Char"
    val Byte      = "Byte"
    val Short     = "Short"
    val Int       = "Int"
    val Long      = "Long"
    val Float     = "Float"
    val Double    = "Double"
  }
}

sealed class ValType(override val name: String)(implicit projectContext: ProjectContext)
  extends StdType(name, Some(StdTypes.instance.AnyVal)) with LeafType {

  override def isFinalType = true
}

class StdTypes(implicit private val projectContext: ProjectContext) extends Disposable {

  lazy val Any = new StdType(Name.Any, None)

  lazy val AnyRef = new StdType(Name.AnyRef, Some(Any))

  lazy val AnyKind = new StdType(Name.AnyKind, None)

  lazy val Null: StdType = new StdType(Name.Null, Some(AnyRef)) {
    override def isFinalType = true
  }

  lazy val Nothing: StdType = new StdType(Name.Nothing, Some(Any)) {
    override def isFinalType = true
  }

  lazy val Singleton: StdType = new StdType(Name.Singleton, Some(AnyRef)) {
    override def isFinalType = true
  }

  lazy val AnyVal = new StdType(Name.AnyVal, Some(Any))

  lazy val Unit = new ValType(Name.Unit)

  lazy val Boolean = new ValType(Name.Boolean)

  lazy val Char = new ValType(Name.Char)

  lazy val Byte = new ValType(Name.Byte)

  lazy val Short = new ValType(Name.Short)

  lazy val Int = new ValType(Name.Int)

  lazy val Long = new ValType(Name.Long)

  lazy val Float = new ValType(Name.Float)

  lazy val Double = new ValType(Name.Double)

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

  override def dispose(): Unit = {
    StdTypes.current.compareAndSet(this, null)
  }
}

object StdTypes {
  private val current = new AtomicReference[StdTypes]()

  def instance(implicit pc: ProjectContext): StdTypes = {
    val last = current.get()
    if (last != null && (last.projectContext == pc)) last
    else {
      val fromContainer = pc.getService(classOf[StdTypes])
      current.compareAndSet(last, fromContainer)
      fromContainer
    }
  }

  def instance2(implicit project: Project): StdTypes = {
    val last = current.get()
    if (last != null && (last.projectContext.project == project)) last
    else {
      val fromContainer = project.getService(classOf[StdTypes])
      current.compareAndSet(last, fromContainer)
      fromContainer
    }
  }
}