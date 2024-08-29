package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.Disposable
import com.intellij.psi.CommonClassNames._
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, LeafType, NamedType, ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType.Name
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util.concurrent.atomic.AtomicReference

sealed class StdType private[api](
  override val name: String,
  val tSuper: Option[StdType]
)(implicit override val projectContext: ProjectContext)
  extends ValueType with NamedType with LeafType {

  val fullName = s"scala.$name"

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitStdType(this)

  /**
   * @return a synthetic class which represents this class if possible<br>
   *         None - in dunmb mode or before synthetic classes are registered
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

    val AnyKind   = "AnyKind"
  }
}

sealed class ValType(override val name: String)(implicit projectContext: ProjectContext)
  extends StdType(name, Some(StdTypes.instance.AnyVal)) with LeafType {

  override def isFinalType = true
}

class StdTypes(implicit private val projectContext: ProjectContext) extends Disposable {
  // Scala 2 library bootstrap classes, which are not present in the `.class` files
  // https://github.com/scala/scala/tree/2.13.x/src/library-aux/scala
  lazy val Any = new StdType(Name.Any, None)
  lazy val AnyRef = new StdType(Name.AnyRef, Some(Any))
  lazy val Null: StdType = new StdType(Name.Null, Some(AnyRef)) {
    override def isFinalType = true
  }
  lazy val Nothing: StdType = new StdType(Name.Nothing, Some(Any)) {
    override def isFinalType = true
  }
  lazy val Singleton: StdType = new StdType(Name.Singleton, Some(AnyRef)) {
    override def isFinalType = true
  }

  // Scala 2 library AnyVal classes
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

  // Scala 3 library
  // - https://www.scala-lang.org/api/3.0.2/scala/AnyKind.html
  // - https://dotty.epfl.ch/docs/reference/other-new-features/kind-polymorphism.html
  lazy val AnyKind = new StdType(Name.AnyKind, None)

  private lazy val all: Seq[StdType] = Seq(
    Any,
    AnyRef,
    Null,
    Nothing,
    Singleton,

    AnyVal,
    Unit,
    Boolean,
    Byte,
    Char,
    Short,
    Int,
    Long,
    Float,
    Double,
  )

  lazy val QualNameToType: Map[String, StdType] =
    all.groupBy(_.fullName).view.mapValues(_.head).toMap

  lazy val fqnBoxedToScType: Map[String, ValType] = Map(
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
}