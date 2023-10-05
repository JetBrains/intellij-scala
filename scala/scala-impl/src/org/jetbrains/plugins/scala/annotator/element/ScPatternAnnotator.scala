package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ComparingUtil.isNeverSubType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, TupleType, TypeParameterType, arrayType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScParameterizedType, ScType}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

object ScPatternAnnotator extends ElementAnnotator[ScPattern] {

  override def annotate(element: ScPattern, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = if (typeAware) {
    if (element.getText.contains("Inner"))
      println(s"===== Checking pattern ${element.getText} in parent ${element.getParent.getText}")
    checkPattern(element, holder)
    if (element.getText.contains("Inner"))
      println("=============================================")
  }

  private def checkPattern(pattern: ScPattern, holder: ScalaAnnotationHolder): Unit = {
    for {
      pType <- patternType(pattern)
      eType <- pattern.expectedType
    } {
      checkPatternType(pType, eType, pattern, holder)
    }
  }

  private def checkPatternType(_patType: ScType, exprType: ScType, pattern: ScPattern, holder: ScalaAnnotationHolder): Unit = {

    val exTp      = widen(exprType)
    val patType   = _patType.removeAliasDefinitions()

    if (pattern.getText.contains("Inner"))
      println(s"Pattern type for ${pattern.getText}: $patType, expected type: $exTp")

    val patternTypeAsTuple =
      ScPattern.ByNameExtractor(pattern).unapply(patType).map {
        productElements =>
          TupleType(productElements)(pattern.elementScope)
      }

    if (patternTypeAsTuple.isDefined)
      println(s"Pattern type as tuple: ${patternTypeAsTuple.get}")

    val neverMatches =
      !matchesPattern(exTp, patternTypeAsTuple.getOrElse(patType)) &&
//      !matchesPattern(exTp, patType) &&
        isNeverSubType(abstraction(patType), exTp) &&
        pattern.typeVariables.isEmpty && freeTypeParamsOfTerms(exprType).isEmpty

    pattern match {
      case _: ScConstructorPattern if neverMatches =>
        val message = ScalaBundle.message("fruitless.type.test", exprType.toString, patType.toString)
        println("Creating a warning")
        holder.createWarningAnnotation(pattern, message)
      case _ =>
    }
  }

  private def widen(scType: ScType): ScType = scType match {
    case designatorOwner: DesignatorOwner if designatorOwner.isSingleton =>
      scType.tryExtractDesignatorSingleton
    case _ =>
      scType.updateLeaves {
        case ScAbstractType(_, _, upper) => upper
        case tpt: TypeParameterType => tpt.upperType
      }
  }

  private def freeTypeParamsOfTerms(tp: ScType): Seq[ScType] = {
    val builder = ArraySeq.newBuilder[ScType]
    tp.visitRecursively {
      case tp: TypeParameterType => builder += tp
      case _ =>
    }
    builder.result()
  }

  private def abstraction(scType: ScType, visited: Set[TypeParameterType] = Set.empty): ScType = {
    scType.updateLeaves {
      case tp: TypeParameterType =>
        if (visited.contains(tp)) tp
        else ScAbstractType(tp.typeParameter,
          abstraction(tp.lowerType, visited + tp),
          abstraction(tp.upperType, visited + tp)
        )
    }
  }

  @tailrec
  private def matchesPattern(matching: ScType, matched: ScType): Boolean =
    matching.weakConforms(matched) || ((matching, matched) match {
      case (arrayType(arg1), arrayType(arg2)) => matchesPattern(arg1, arg2)
      case (_, parameterized: ScParameterizedType) =>
        val newtp = abstraction(parameterized)
        !matched.equiv(newtp) && matching.weakConforms(newtp)
      case _ => false
    })

  def patternType(pattern: ScPattern): Option[ScType] = {
    import pattern.projectContext

    def constrPatternType(patternRef: ScStableCodeReference): Option[ScType] = {
      patternRef.bind() match {
        case Some(srr) =>
          srr.getElement match {
            case fun: ScFunction if fun.parameters.count(!_.isImplicitParameter) == 1 =>
              fun.parametersTypes.headOption
                .map(srr.substitutor)
            case _ => None
          }
        case None => None
      }
    }

    pattern match {
      case c: ScConstructorPattern =>
        constrPatternType(c.ref)
      case _: ScReferencePattern | _: ScWildcardPattern =>
        Some(Any) //these only have expected type
      case _ => None
    }
  }
}