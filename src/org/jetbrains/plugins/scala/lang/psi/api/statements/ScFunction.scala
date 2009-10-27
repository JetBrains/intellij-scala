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
import nonvalue.ScMethodType
import result.{Failure, Success, TypingContext, TypeResult}

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
  def methodType: TypeResult[ScMethodType] = {
    // collect parameter types
    val clauseTypes: Seq[Seq[TypeResult[ScType]]] = clauses match {
      case None => Nil
      case Some(paramClauses) => {
        val cls = paramClauses.clauses
        if (cls.length == 0) Nil
        else cls.map {
          cl => {
            val params = cl.parameters
            if (params.length == 0) Seq(Success(Unit, Some(cl)))
            else params.map(_.getType(TypingContext.empty))
          }
        }
      }
    }

    // return Type
    val rt = returnType.getOrElse(Any)
    val mtype = Success((clauseTypes match {
      case Nil => ScMethodType(rt, Nil)
      case _ => clauseTypes.foldRight(rt) {
        (clz, tpe) =>
          collectFailures(clz, Nothing)(ScMethodType(tpe, _)).getOrElse(Any)
      }
    }).asInstanceOf[ScMethodType], Some(this))

    

    (for (f@Failure(_, _) <- (returnType :: clauseTypes.flatten.toList)) yield f).foldLeft(mtype)(_.apply(_))
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
}