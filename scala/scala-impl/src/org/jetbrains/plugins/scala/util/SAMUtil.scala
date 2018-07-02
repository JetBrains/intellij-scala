package org.jetbrains.plugins.scala.util

import com.intellij.psi.{PsiClass, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScFunctionExpr, ScUnderScoreSectionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScType}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, ModCount}

object SAMUtil {
  implicit class ScExpressionExt(val expr: ScExpression) extends AnyVal {
    @CachedInUserData(expr, ModCount.getBlockModificationCount)
    def samTypeParent: Option[PsiClass] =
      if (expr.isSAMEnabled) expr match {
        case HasExpectedClassType(tpe, cls)
            if isFunctionalExpression(expr) && ScalaPsiUtil.toSAMType(tpe, expr).isDefined =>
          Option(cls)
        case _ => None
      } else None
  }

  object SAMTypeParent {
    def unapply(e: ScExpression): Option[PsiClass] = e.samTypeParent
  }

  def isFunctionalExpression(e: ScExpression): Boolean = e match {
    case _: ScFunctionExpr                                    => true
    case block: ScBlock if block.isAnonymousFunction          => true
    case MethodValue(_)                                       => true
    case _ if ScUnderScoreSectionUtil.underscores(e).nonEmpty => true
    case _                                                    => false
  }

  private object HasExpectedClassType {
    def unapply(e: ScExpression): Option[(ScType, PsiClass)] =
      for {
        tpe    <- e.expectedType(fromUnderscore = false)
        aClass <- tpe.extractClass
      } yield (tpe, aClass)
  }

  def singleAbstractMethodOf(cls: PsiClass): Option[PsiMethod] = cls match {
    case tDef: ScTemplateDefinition =>
      val abstractMembers = tDef.allSignatures.filter(TypeDefinitionMembers.ParameterlessNodes.isAbstract)

      abstractMembers match {
        case Seq(PhysicalSignature(fun: ScFunction, _)) => Some(fun).filterNot(_.hasTypeParameters)
        case _                                          => None
      }
    case _ =>
      import scala.collection.JavaConverters._
      def isAbstract(m: PsiMethod): Boolean =
        m.hasAbstractModifier && m.findSuperMethods().forall(_.hasAbstractModifier)

      val visibleMethods = cls.getVisibleSignatures.asScala.map(_.getMethod).toList
      visibleMethods.filter(isAbstract) match {
        case abstractMethod :: Nil => Option(abstractMethod).filterNot(_.hasTypeParameters)
        case _                     => None
      }
  }
}
