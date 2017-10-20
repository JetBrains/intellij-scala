package org.jetbrains.sbt

/**
 * @author Nikolay Obedin
 * @since 8/4/15.
 */
object DslUtils {

  /**
   * Key for AttributeMap. Store some value of T under given key
   */
  class Attribute[T](val key: String)

  /**
   * Type-safe storage for attributes
   */
  class AttributeMap {
    private var attributes = Map.empty[(Attribute[_], String), Any]

    def get[T](attribute: Attribute[T])(implicit m: Manifest[T]): Option[T] =
      attributes.get((attribute, m.toString)).map(_.asInstanceOf[T])

    def getOrFail[T : Manifest](attribute: Attribute[T]): T =
      get(attribute).getOrElse(throw new Error(s"Value for '${attribute.key}' is not found"))

    def put[T](attribute: Attribute[T], value: T)(implicit m: Manifest[T]): Unit =
      attributes = attributes + ((attribute, m.toString) -> value)
  }

  /**
   * Assignment to specific attribute
   * Implicit conversion to this class is used to create a fancy DSL
   */
  class AttributeDef[T : Manifest](attribute: Attribute[T], attributes: AttributeMap) {
    def :=(newValue: => T): Unit =
      attributes.put(attribute, newValue)
  }

  /**
   * Appending and concatenating values of attributes that have sequential type
   * Implicit conversion to this class is used to create a fancy DSL
   */
  class AttributeSeqDef[T](attribute: Attribute[Seq[T]], attributes: AttributeMap)(implicit m: Manifest[Seq[T]]) {
    def +=(newValue: => T): Unit = {
      val newSeq = attributes.get(attribute).getOrElse(Seq.empty) :+ newValue
      attributes.put(attribute, newSeq)
    }
    def ++=(newSeq: => Seq[T]): Unit = {
      val seqConcat = attributes.get(attribute).getOrElse(Seq.empty) ++ newSeq
      attributes.put(attribute, seqConcat)
    }
  }
}
