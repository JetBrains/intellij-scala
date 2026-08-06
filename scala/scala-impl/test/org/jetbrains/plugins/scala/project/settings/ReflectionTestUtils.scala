package org.jetbrains.plugins.scala.project.settings

import org.junit.Assert.{assertEquals, fail}

import java.lang.reflect.{Field, Modifier}
import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

object ReflectionTestUtils {

  private val NonDefaultStringValue = "non-default-string-value"

  /**
   * Creates an instance of class using default constructor and sets all public field values to non-default values
   */
  def createInstanceWithNonDefaultValues[T](clazz: Class[T]): T = {
    val instance = clazz.getDeclaredConstructor().newInstance()
    val fields = getPublicFields(clazz)
    for (field <- fields) {
      setNonDefaultFieldValue(instance, field)
    }
    instance
  }

  def getPublicFields[T](clazz: Class[T]): Array[Field] =
    clazz.getDeclaredFields.filter(f => !Modifier.isPrivate(f.getModifiers))

  def setNonDefaultFieldValue[T](instance: T, field: Field): Unit = {
    val fieldDefaultValue: AnyRef = field.get(instance)
    val fieldType: Class[_] = field.getType

    if (fieldType.isEnum) {
      val enumValues: Array[_] = fieldType.getEnumConstants
      val defaultOrdinal = fieldDefaultValue.asInstanceOf[java.lang.Enum[_]].ordinal()
      val nonDefaultOrdinal = (defaultOrdinal + 1) % enumValues.length
      val nonDefaultValue = enumValues(nonDefaultOrdinal)
      field.set(instance, nonDefaultValue)
    }
    else if (fieldType eq classOf[Boolean])
      field.set(instance, !fieldDefaultValue.asInstanceOf[Boolean])
    else if (fieldType eq classOf[Int])
      field.set(instance, fieldDefaultValue.asInstanceOf[Int] + 1)
    else if (fieldType eq classOf[Long])
      field.set(instance, fieldDefaultValue.asInstanceOf[Long] + 1)
    else if (fieldType eq classOf[Float])
      field.set(instance, fieldDefaultValue.asInstanceOf[Float] + 1)
    else if (fieldType eq classOf[Double])
      field.set(instance, fieldDefaultValue.asInstanceOf[Double] + 1)
    else if (fieldType eq classOf[Short])
      field.set(instance, fieldDefaultValue.asInstanceOf[Short] + 1)
    else if (fieldType eq classOf[Byte])
      field.set(instance, fieldDefaultValue.asInstanceOf[Byte] + 1)
    else if (fieldType eq classOf[String])
      field.set(instance, NonDefaultStringValue)
    else if (fieldType eq classOf[util.List[_]]) {
      //Assuming it's list of Strings
      field.set(instance, java.util.List.of(NonDefaultStringValue))
    }
    else if (fieldType eq classOf[Array[String]]) {
      field.set(instance, Array(NonDefaultStringValue))
    }
    else
      fail(s"Unsupported field type: $fieldType")
  }

  private def getFieldValues(instance: Any): Seq[(String, AnyRef)] = {
    val fields = getPublicFields(instance.getClass).toSeq
    fields.map { field =>
      field.getName -> field.get(instance)
    }
  }

  private def getFieldValuesText(instance: Any): String = {
    val values = getFieldValues(instance)
    values.map { case (name -> value) =>
      name -> (value match {
        case array: Array[_] => array.mkString(", ")
        case iterable: Iterable[_] => iterable.mkString(", ")
        case collection: java.util.Collection[_] => collection.asScala.mkString(", ")
        case other => other
      })
    }.mkString("\n")
  }

  def assertEqualsWithFieldValuesDiff[T](message: String, expected: T, actual: T): Unit = {
    if (expected != actual) {
      assertEquals(
        message,
        s"""$expected
           |${getFieldValuesText(expected)}""".stripMargin,
        s"""$actual
           |${getFieldValuesText(actual)}""".stripMargin,
      )
    }
  }
}
