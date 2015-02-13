package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, ExpressionType}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.ScType.ExtractClass

import scala.collection.GenSeq

/**
 * @author Nikolay.Tropin
 */
class ZeroIndexToHeadInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(new ZeroIndexToHead(this))
}

class ZeroIndexToHead(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def hint: String = InspectionBundle.message("replace.with.head")

  override def getSimplification(single: MethodRepr): List[Simplification] = {
    val genSeqType =
      ScalaPsiElementFactory.createTypeElementFromText("scala.collection.GenSeq[_]", single.itself.getContext, single.itself).calcType
    single.itself match {
      case MethodRepr(_, Some(ExpressionType(tp)), None, Seq(zero)) if zero.getText == "0" && tp.conforms(genSeqType) =>
        createSimplification(single, single.itself, "head", Seq.empty)
      case MethodRepr(_, Some(ExpressionType(tp)), Some(ref), Seq(zero))
        if zero.getText == "0" && ref.refName == "apply" && tp.conforms(genSeqType) =>
        createSimplification(single, single.itself, "head", Seq.empty)
      case _ => Nil
    }
  }
}
