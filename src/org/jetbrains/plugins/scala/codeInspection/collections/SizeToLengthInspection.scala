package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ExpressionType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScParameterizedType, ScType}

/**
 * @author Nikolay.Tropin
 */
class SizeToLengthInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(new SizeToLength(this))
}

class SizeToLength(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def hint: String = InspectionBundle.message("size.to.length")

  override def getSimplification(single: MethodRepr): List[Simplification] = {
    (single.optionalBase, single.optionalMethodRef) match {
      case (Some(ExpressionType(tpe)), Some(ref))
        if ref.refName == "size" && isCollectionMethod(ref) && (isArray(tpe) || isString(tpe)) =>
        createSimplification(single, single.itself, "length", Seq.empty)
      case _ => Nil
    }
  }
  
  def isString(tp: ScType) = {
    val extracted = ScType.extractDesignatorSingletonType(tp).getOrElse(tp)
    val canonicalText = extracted.canonicalText
    canonicalText == "_root_.java.lang.String" || canonicalText == "_root_.scala.Predef.String"
  }
  
  def isArray(tp: ScType) = ScType.extractDesignatorSingletonType(tp).getOrElse(tp) match {
    case ScParameterizedType(ScDesignatorType(ClassQualifiedName("scala.Array")), _) => true
    case _ => false
  }
}
