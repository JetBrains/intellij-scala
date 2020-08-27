package org.jetbrains.plugins.scala
package dfa

import org.jetbrains.plugins.scala.dfa.lattice.{BinaryLattice, FlatLattice, PowerSetLattice, ProductLattice}

import scala.util.hashing.MurmurHash3


/*************************** Any (Top) **************************/
sealed trait DfAny extends Product with Serializable {
  def toBoolLat: BoolLat = BoolLat.Bottom
}
object DfAny {
  lazy val Top: DfAny = join[DfAny](DfAnyVal.Top, DfAnyRef.Top)
  sealed trait Concrete extends DfAny
  val Bottom: DfNothing.type = DfNothing

  implicit val lattice: Lattice[DfAny] = new ProductLattice[DfAny](Top, Bottom, Array(DfAnyVal.lattice, DfAnyRef.lattice)) {
    override protected def createTuple(elements: Array[DfAny]): DfAny = {
      if (elements.forall(_ == DfNothing)) DfNothing
      else {

        DfAnyProductTuple(elements)
      }
    }

    override protected def indexOf(element: DfAny): Option[Int] = element match {
      case DfNothing => None
      case _: DfAnyVal => Some(0)
      case _: DfAnyRef => Some(1)
      case _ => throw new RuntimeException("Unreachable Code")
    }

    private case class DfAnyProductTuple(override val elements: Array[DfAny]) extends ProductTupleBase with DfAny {
      override def toString: String =
        if (this == Top) "DfAny.Top"
        else elements.filterNot(_ == DfNothing).mkString(" | ")
      override def hashCode(): Int = elements.hashCode() + 2212
      override def equals(o: Any): Boolean = o match {
        case o: DfAnyProductTuple => elements.sameElements(o.elements)
        case _ => false
      }
    }
  }
}


/**************************** AnyRef ****************************/
sealed trait DfAnyRef extends DfAny
object DfAnyRef {
  case object Top extends DfAnyRef {
    override def toString: String = "DfAnyRef.Top"
  }
  sealed class Concrete extends DfAnyRef with DfAny.Concrete {
    override def productElement(n: Int): Any = throw new IndexOutOfBoundsException(n.toString)
    override def productArity: Int = 0
    override def canEqual(that: Any): Boolean = this == that
  }
  val Bottom: DfAnyRef = DfNothing

  implicit val lattice: Lattice[DfAnyRef] = new PowerSetLattice[DfAnyRef](Top, Bottom) {
    override protected def createSet(elements: Set[DfAnyRef]): DfAnyRef = elements.size match {
      case 0 => DfNothing
      case 1 => elements.head
      case _ =>
        case class DfAnyRefPowerSet(override val elements: Set[DfAnyRef]) extends PowerSetBase with DfAnyRef {
          override def toString: String = elements.mkString(" | ")
        }
        DfAnyRefPowerSet(elements)
    }
  }
}


/*********************** Special AnyRefs ************************/
class DfStringRef(val text: String) extends DfAnyRef.Concrete {
  override def toString: String = "\"" + text + "\""
}


/**************************** AnyVal ****************************/
sealed trait DfAnyVal extends DfAny
object DfAnyVal {
  lazy val Top: DfAnyVal = join[DfAnyVal](DfUnit.Top, DfBool.Top, DfInt.Top)
  sealed trait Concrete extends DfAnyVal with DfAny.Concrete
  val Bottom: DfAnyVal = DfNothing

  implicit val lattice: Lattice[DfAnyVal] = new ProductLattice[DfAnyVal](Top, Bottom, Array(DfUnit.lattice, DfBool.lattice, DfInt.lattice)) {
    override protected def createTuple(elements: Array[DfAnyVal]): DfAnyVal = {
      if (elements.forall(_ == DfNothing)) DfNothing
      else {
        DfAnyValProductTuple(elements)
      }
    }

    override protected def indexOf(element: DfAnyVal): Option[Int] = element match {
      case DfNothing => None
      case _: DfUnit => Some(0)
      case _: DfBool => Some(1)
      case _: DfInt.Abstract => Some(2)
      case _ =>  throw new RuntimeException("Unreachable Code")
    }

    private case class DfAnyValProductTuple(override val elements: Array[DfAnyVal]) extends ProductTupleBase with DfAnyVal {
      override def toString: String =
        if (this == Top) "DfAnyVal.Top"
        else elements.filterNot(_ == DfNothing).mkString(" | ")
      override def hashCode(): Int = MurmurHash3.arrayHash(elements) + 333
      override def equals(o: Any): Boolean = o match {
        case o: DfAnyValProductTuple => elements.sameElements(o.elements)
        case _ => false
      }
    }
  }
}


/**************************** Boolean ****************************/
trait DfBool extends DfAnyVal
object DfBool {
  case object Top extends DfBool {
    override def toBoolLat: BoolLat = BoolLat.Top
    override def toString: String = "DfBool.Top"
  }
  sealed trait Concrete extends DfBool with DfAnyVal.Concrete
  object Concrete {
    def apply(bool: Boolean): Concrete =
      if (bool) True else False

    def unapply(bool: Concrete): Some[Boolean] = Some(bool == True)
  }

  case object True extends Concrete {
    override def toBoolLat: BoolLat = BoolLat.True
    override def toString: String = "DfTrue"
  }
  case object False extends Concrete {
    override def toBoolLat: BoolLat = BoolLat.False
    override def toString: String = "DfFalse"
  }

  final val Bottom: DfBool = DfNothing

  implicit val lattice: Lattice[DfBool] = new FlatLattice[DfBool](Top, Bottom)

  def apply(bool: Boolean): Concrete = Concrete(bool)

  def apply(maybeBool: Option[Boolean]): DfBool =
    maybeBool.fold(Bottom)(Concrete(_))

  def apply(boolLat: BoolLat): DfBool = boolLat match {
    case BoolLat.Top => Top
    case BoolLat.True => True
    case BoolLat.False => False
    case BoolLat.Bottom => DfNothing
  }
}


/***************************** Numerics **************************/
sealed trait DfAbstractNumeric extends DfAnyVal {
  def kind: DfNumeric.Kind
}

sealed trait DfConcreteNumeric extends DfAbstractNumeric

object DfNumeric {
  sealed abstract class Kind(val size: Int)
  sealed abstract class IntegerKind(val min: Long, val max: Long, _size: Int) extends Kind(_size)
  final object ByteKind extends IntegerKind(Byte.MinValue, Byte.MaxValue, java.lang.Byte.BYTES)
  final object CharKind extends IntegerKind(Char.MinValue, Char.MaxValue, java.lang.Character.BYTES)
  final object ShortKind extends IntegerKind(Short.MinValue, Short.MaxValue, java.lang.Short.BYTES)
  final object IntKind extends IntegerKind(Int.MinValue, Int.MaxValue, java.lang.Integer.BYTES)
  final object LongKind extends IntegerKind(Long.MinValue, Long.MaxValue, java.lang.Long.BYTES)

  sealed abstract class FloatingPointKind(_size: Int) extends Kind(_size)
  final object FloatKind extends FloatingPointKind(java.lang.Float.BYTES)
  final object DoubleKind extends FloatingPointKind(java.lang.Double.BYTES)

  abstract class KindFactory[@specialized(Byte, Char, Short, Int, Long, Float, Double) T <: AnyVal](val kind: Kind, name: String) {
    sealed trait Abstract extends DfAbstractNumeric {
      override def kind: Kind = KindFactory.this.kind
    }

    case object Top extends Abstract {
      override def toString: String = s"$name.Top"
    }
    case class Concrete(value: T) extends Abstract {
      override def toString: String = s"$name[$value]"
    }
    final val Bottom: Abstract = initialBottom

    implicit final val lattice: Lattice[Abstract] = new FlatLattice[Abstract](Top, Bottom)

    def apply(num: T): Concrete = Concrete(num)
    def apply(num: Option[T]): Abstract = num.fold(Bottom)(Concrete.apply)

    protected[this] def initialBottom: Abstract
  }
}

object DfInt extends DfNumeric.KindFactory[Int](DfNumeric.IntKind, "DfInt") { protected[this] override def initialBottom: Abstract = DfNothing }


/****************************** Unit ****************************/
sealed trait DfUnit extends DfAnyVal
case object DfUnit  {
  val Top: Concrete = Concrete
  type Concrete = Concrete.type
  case object Concrete extends DfUnit with DfAnyVal.Concrete {
    override def toString: String = "DfUnit.Top"
  }
  val Bottom: DfUnit = DfNothing

  implicit val lattice: Lattice[DfUnit] = new BinaryLattice(Top, Bottom)
}


/************************* Nothing (Bottom) **********************/
sealed trait DfNothing
  extends DfAny
    with DfAnyRef
    with DfUnit
    with DfBool
    with DfInt.Abstract
case object DfNothing extends DfNothing
