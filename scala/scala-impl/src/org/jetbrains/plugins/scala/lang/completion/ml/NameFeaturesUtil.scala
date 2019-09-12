package org.jetbrains.plugins.scala.lang.completion.ml

import com.intellij.util.text.NameUtilCore
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, JavaArrayType, ParameterizedType, PartialFunctionType, StdType, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScCompoundType, ScExistentialArgument, ScExistentialType, ScLiteralType, ScType, ScalaTypeVisitor}

import scala.collection.mutable

object NameFeaturesUtil {

  private val NonSymbolicPattern = """[\w$]""".r
  private val NonNamePattern = """[^a-zA-Z_]""".r

  val MaxWords = 7

  def isSymbolic(name: String): Boolean = NonSymbolicPattern.findFirstIn(name).isEmpty

  def extractWords(names: Iterable[String]): Array[String] = {
    val result = mutable.Set.empty[String]

    def canContinueWith(string: String) = result.size < MaxWords && meaningful(string)

    for (name <- names) {
      if (canContinueWith(name)) {
        for (namePart <- NonNamePattern.split(name)) {
          if (canContinueWith(namePart)) {
            for (word <- NameUtilCore.splitNameIntoWords(namePart)) {
              if (canContinueWith(word)) {
                result += word.toLowerCase
              }
            }
          }
        }
      }
    }

    result.toArray
  }

  def extractWords(maybeType: Option[ScType]): Array[String] = {

    val typesSortedByRelevance = maybeType match {
      case Some(FunctionType(returnType, argTypes)) => returnType +: argTypes
      case Some(PartialFunctionType(returnType, argType)) => Seq(returnType, argType)
      case Some(scType) => Seq(scType)
      case _ => Seq.empty
    }

    val names = mutable.Set.empty[String]

    for (scType <- typesSortedByRelevance) {
      val namesLeft = MaxWords - names.size
      if (namesLeft > 0) {
        val visitor = new TypeNamesExtractor(namesLeft)

        scType.visitType(visitor)

        names ++= visitor.names
      }
    }

    extractWords(names)
  }

  def wordsSimilarity(expectedWords: Array[String], candidates: Array[String]): Float = {
    var result = 0f

    for (expectedWord <- expectedWords) {
      for (candidate <- candidates) {
        result += relativePrefixMath(expectedWord, candidate)
      }
    }

    if (expectedWords.nonEmpty) result / expectedWords.length else -1
  }

  private def relativePrefixMath(str1: String, str2: String): Float = {
    val minLength = str1.length min str2.length
    val maxLength = str1.length max str2.length

    if (maxLength == 0) {
      return 0
    }

    for (i <- 0 until minLength) {
      if (str1(i) != str2(i)) {
        return i.toFloat / maxLength
      }
    }

    minLength.toFloat / maxLength
  }

  private def meaningful(string: String): Boolean = {
    string.length > 2 && string != "get" && string != "set" && !isSymbolic(string)
  }

  private class TypeNamesExtractor(maxNames: Int) extends ScalaTypeVisitor {
    private val result = mutable.Set.empty[String]
    private var visited = 0

    def names: Seq[String] = result.toSeq

    override def visitStdType(`type`: StdType): Unit = add(`type`.name)

    override def visitTypeParameterType(`type`: TypeParameterType): Unit = add(`type`.name)

    override def visitDesignatorType(d: ScDesignatorType): Unit = add(d.element.name)

    override def visitExistentialArgument(s: ScExistentialArgument): Unit = add(s.name)

    override def visitJavaArrayType(`type`: JavaArrayType): Unit = visit(`type`.argument)

    override def visitCompoundType(c: ScCompoundType): Unit = c.components.foreach(visit)

    override def visitExistentialType(e: ScExistentialType): Unit = visit(e.simplify())

    override def visitTypePolymorphicType(t: ScTypePolymorphicType): Unit = visit(t.internalType)

    override def visitProjectionType(p: ScProjectionType): Unit = {
      add(p.element.name)
      visit(p.projected)
    }

    override def visitParameterizedType(`type`: ParameterizedType): Unit = {
      visit(`type`.designator)
      `type`.typeArguments.foreach(visit)
    }

    override def visitMethodType(`type`: ScMethodType): Unit = {
      visit(`type`)
      `type`.params.filterNot(_.isImplicit).map(_.paramType).foreach(visit)
    }

    override def visitUndefinedType(`type`: UndefinedType): Unit = ()

    override def visitThisType(t: ScThisType): Unit = ()

    override def visitLiteralType(l: ScLiteralType): Unit = ()

    override def visitAbstractType(a: ScAbstractType): Unit = ()

    private def add(name: String): Unit = {
      if (meaningful(name)) {
        result += name
      }
    }

    private def visit(`type`: ScType): Unit = {
      visited += 1
      if (result.size < maxNames && visited < 15) {
        `type`.visitType(this)
      }
    }
  }
}
