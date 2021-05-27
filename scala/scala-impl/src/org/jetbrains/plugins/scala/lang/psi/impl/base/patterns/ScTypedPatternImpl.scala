package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{PsiTypeExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScBindingPatternStub
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialType, api, _}

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScTypedPatternImpl private(stub: ScBindingPatternStub[ScTypedPattern], node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.TYPED_PATTERN, node)
    with ScPatternImpl
    with ScTypedPattern
    with TypedPatternLikeImpl
{
  def this(node: ASTNode) = this(null, node)

  def this(stub: ScBindingPatternStub[ScTypedPattern]) = this(stub, null)

  override def nameId: PsiElement = findChildByType[PsiElement](TokenSets.ID_SET)

  override def toString: String = "TypedPattern: " + ifReadAllowed(name)("")

  override def `type`(): TypeResult = {
    typePattern match {
      case Some(tp) =>
        if (tp.typeElement == null) return Failure(ScalaBundle.message("no.type.element.for.type.pattern"))
        val typeElementType: TypeResult =
          tp.typeElement.`type`().map {
            case tp: ScExistentialType =>
              val skolem = tp.quantified
              skolem.extractClassType match {  //todo: type aliases?
                case Some((clazz: ScTypeDefinition, subst)) =>
                  val typeParams = clazz.typeParameters
                  skolem match {
                    case ParameterizedType(des, typeArgs) if typeArgs.length == typeParams.length =>
                      ScParameterizedType(des, typeArgs.zip(typeParams).map {
                        case (arg: ScExistentialArgument, param: ScTypeParam) =>
                          val lowerBound =
                            if (arg.lower.equiv(Nothing)) subst(param.lowerBound.getOrNothing)
                            else arg.lower //todo: lub?
                          val upperBound =
                            if (arg.upper.equiv(Any)) subst(param.upperBound.getOrAny)
                            else arg.upper //todo: glb?
                          ScExistentialArgument(arg.name, arg.typeParameters, lowerBound, upperBound)
                        case (tp: ScType, _: ScTypeParam) => tp
                      }).unpackedType
                    case _ => tp
                  }
                case Some((clazz: PsiClass, subst)) =>
                  val typeParams: Array[PsiTypeParameter] = clazz.getTypeParameters
                  skolem match {
                    case ParameterizedType(des, typeArgs) if typeArgs.length == typeParams.length =>
                      ScParameterizedType(des, typeArgs.zip(typeParams).map {
                        case (arg: ScExistentialArgument, param: PsiTypeParameter) =>
                          val lowerBound = arg.lower
                          val upperBound =
                            if (arg.upper.equiv(api.Any)) {
                              val listTypes: Array[PsiClassType] = param.getExtendsListTypes
                              if (listTypes.isEmpty) api.Any
                              else subst(listTypes.toSeq.map(_.toScType()).glb(checkWeak = true))
                            } else arg.upper //todo: glb?
                          ScExistentialArgument(arg.name, arg.typeParameters, lowerBound, upperBound)
                        case (tp: ScType, _) => tp
                      }).unpackedType
                    case _ => tp
                  }
                case _ => tp
              }
            case tp: ScType => tp
          }
        this.expectedType match {
          case Some(expectedType) =>
            typeElementType.map(resType => expectedType.glb(resType, checkWeak = false))
          case _ => typeElementType
        }
      case None => Failure(ScalaBundle.message("no.type.pattern"))
    }
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, `type`())
  }

  override def getOriginalElement: PsiElement = super[ScTypedPattern].getOriginalElement
}
