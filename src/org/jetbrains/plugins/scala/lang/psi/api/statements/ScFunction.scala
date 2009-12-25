package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements


import collection.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

import psi.stubs.ScFunctionStub
import types._
import nonvalue.{TypeParameter, ScTypePolymorphicType, NonValueType, ScMethodType}
import result.{Failure, Success, TypingContext, TypeResult}
import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

/**
 * @author Alexander Podkhalyuzin
 */

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun extends ScTypeParametersOwner {
  def retType: ScType

  def paramTypes: Seq[ScType]

  def typeParameters: Seq[ScTypeParam]
}


/**
 * Represents Scala's internal function definitions and declarations
 */
trait ScFunction extends ScalaPsiElement with ScMember with ScTypeParametersOwner
        with PsiMethod with ScParameterOwner with ScDocCommentOwner with ScTypedDefinition
        with ScDeclaredElementsHolder with ScAnnotationsHolder {
  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  /**
   * Returns pure `function' type as it was defined as a field with functional value
   */
  def methodType: ScMethodType = {
    val parameters: ScParameters = paramClauses
    val clauses = parameters.clauses
    if (clauses.length == 0) return ScMethodType(returnType.getOrElse(Any), Seq.empty, false)
    val res = clauses.foldRight[ScType](returnType.getOrElse(Any)){(clause: ScParameterClause, tp: ScType) =>
      ScMethodType(tp, clause.getSmartParameters, clause.isImplicit)
    }
    res.asInstanceOf[ScMethodType]
  }

  /**
   * Returns internal type with type parameters.
   */
  def polymorphicType: ScType = {
    if (typeParameters.length == 0) return methodType
    else return ScTypePolymorphicType(methodType, typeParameters.map(tp =>
      TypeParameter(tp.name, tp.lowerBound.getOrElse(Nothing), tp.upperBound.getOrElse(Any), tp)))
  }

  /**
   * Optional Type Element, denotion function's return type
   * May be omitted for non-recursive functions
   */
  def returnTypeElement: Option[ScTypeElement] = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub = st.getStub
        if (stub != null) {
          return stub.asInstanceOf[ScFunctionStub].getReturnTypeElement
        }
      }
      case _ =>
    }
    findChild(classOf[ScTypeElement])
  }

  def paramClauses: ScParameters

  def returnType: TypeResult[ScType]

  def declaredType: TypeResult[ScType] = wrap(returnTypeElement) flatMap (_.getType(TypingContext.empty))

  def clauses: Option[ScParameters] = Some(paramClauses)

  def parameters: Seq[ScParameter]

  def paramTypes: Seq[ScType] = parameters.map {_.getType(TypingContext.empty).getOrElse(Nothing)}

  def declaredElements = Seq.singleton(this)

  def superMethods: Seq[PsiMethod]

  def superMethod: Option[PsiMethod]

  def superSignatures: Seq[FullSignature]

  def hasParamName(name: String, clausePosition: Int = -1): Boolean = getParamByName(name, clausePosition) != None

  def getParamByName(name: String, clausePosition: Int = -1): Option[ScParameter] = {
    clausePosition match {
      case -1 => {
        for (param <- parameters if param.name == name) return Some(param)
        return None
      }
      case i if i < 0 => return None
      case i if i >= allClauses.length => return None
      case i => {
        val clause: ScParameterClause = allClauses.apply(i)
        for (param <- clause.parameters if param.name == name) return Some(param)
        return None
      }
    }
  }

  /**
   * Does the function have `=` between the signature and the implementation?
   */
  def hasAssign: Boolean


  override def accept(visitor: ScalaElementVisitor) = visitor.visitFunction(this)
}