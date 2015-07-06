package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScParameterizedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameterizedTypeElement {
  override def toString: String = "ParametrizedTypeElement: " + getText

  def typeArgList = findChildByClass(classOf[ScTypeArgs])

  def typeElement = findChildByClass(classOf[ScTypeElement])

  def findConstructor = {
    getContext match {
      case constr: ScConstructor => Some(constr)
      case _ => None
    }
  }

  private var desugarizedTypeModCount: Long = 0L
  private var desugarizedType: Option[ScTypeElement] = null

  //computes desugarized type either for existential type or one of kind projector types
  def computeDesugarizedType: Option[ScTypeElement] = {
    def kindProjectorFunctionSyntax(fun: ScFunctionalTypeElement): Option[ScTypeElement] = {
      val param = fun.paramTypeElement
      val ret: ScTypeElement = fun.returnTypeElement match {
          case Some(r) => r
          case _ => return None
        }
      val lambdaTypeBuilder = new StringBuilder
      val typeName = "Λ$"
      lambdaTypeBuilder.append(s"({type $typeName[")
      val paramText: String = param.getText match {
        case coOrContravariant if coOrContravariant.contains('-') || coOrContravariant.contains('+') =>
          val args = param.depthFirst.filter(_.isInstanceOf[ScSimpleTypeElement])

          val paramT = new StringBuilder("(")
          for (a <- args) {
            val text =
              if (a.getText.contains("`")) a.getText.replaceAll("`", "")
              else a.getText
            paramT.append(text)
            if (args.hasNext && a.getText != "-" && a.getText != "+") {
              paramT.append(", ")
            }
          }
          paramT.append(")")
          paramT.toString()
        case a => a.replaceAll("`", "")
      }
      lambdaTypeBuilder.append(paramText.replaceAll("[()]", ""))
      lambdaTypeBuilder.append(s"] = ${ret.getText}})#$typeName")
      val newTE = ScalaPsiElementFactory.createTypeElementFromText(lambdaTypeBuilder.toString(), getContext, this)
      Option(newTE)
    }

    def kindProjectorInlineSyntax(e: PsiElement): Option[ScTypeElement] = {
      def generateName(i: Int): String = { //kind projector generates names the same way
        val res = ('α' + (i % 25)).toChar.toString
        if (i < 25) res
        else res + (i / 25)
      }

      val typeName = "Λ$"
      val inlineTypeBuilder = new StringBuilder
      val qMark = this.findElementAt(e.getText.indexOf("?"))
      val parent = qMark.parents.find(_.isInstanceOf[ScParameterizedTypeElement]).getOrElse( return None )
      val parameterized = parent.asInstanceOf[ScParameterizedTypeElement]
      val args: Seq[(String, Boolean)] = parameterized.typeArgList.typeArgs.zipWithIndex.map {
        case (qm, i) if qm.getText.contains("?") => (qm.getText.replace("?", generateName(i)), true)
        case (a, _) => (a.getText, false)
      }
      inlineTypeBuilder.append(s"({type $typeName")
      val paramString = args.collect {
        case (name, true) => name
      }.mkString(start = "[", sep = ", ", end = "]")
      inlineTypeBuilder.append(paramString)
      inlineTypeBuilder.append(s" = ${parameterized.typeElement.getText}")
      val body = args.map { e =>
        e._1.replaceAll("[\\+\\-]", "").replaceAll("\\[.*\\]", "") //remove all square brackets, `+` and `-`
      }
      inlineTypeBuilder.append(body.mkString(start = "[", sep = ", ", end = "]"))
      inlineTypeBuilder.append(s"})#$typeName")
      val newTE = ScalaPsiElementFactory.createTypeElementFromText(inlineTypeBuilder.toString(), getContext, this)
      Option(newTE)
    }

    def existentialType: Option[ScTypeElement] = {
      val forSomeBuilder = new StringBuilder
      var count = 1
      forSomeBuilder.append(" forSome {")
      val typeElements = typeArgList.typeArgs.map {
        case w: ScWildcardTypeElement =>
          forSomeBuilder.append("type _" + "$" + count +
            w.lowerTypeElement.fold("")(te => s" >: ${te.getText}") +
            w.upperTypeElement.fold("")(te => s" <: ${te.getText}"))
          forSomeBuilder.append("; ")
          val res = s"_$$$count"
          count += 1
          res
        case t => t.getText
      }
      forSomeBuilder.delete(forSomeBuilder.length - 2, forSomeBuilder.length)
      forSomeBuilder.append("}")
      val newTypeText = s"(${typeElement.getText}${typeElements.mkString("[", ", ", "]")} ${forSomeBuilder.toString()})"
      val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newTypeText, getContext, this)
      Option(newTypeElement)
    }

    def inner(): Option[ScTypeElement] = {
      val kindProjectorEnabled = ScalaPsiUtil.kindProjectorPluginEnabled(this)
      typeArgList.typeArgs.find {
        case e: ScWildcardTypeElementImpl => true
        case _: ScFunctionalTypeElement if kindProjectorEnabled => true
        case e if kindProjectorEnabled && e.children.exists(_.getText.matches("[+-]?\\?")) => true
        case _ => false
      } match {
        case Some(fun: ScFunctionalTypeElement) if kindProjectorEnabled => kindProjectorFunctionSyntax(fun)
        case Some(e) if kindProjectorEnabled && e.getText.contains("?") => kindProjectorInlineSyntax(e)
        case Some(_) => existentialType
        case _ => None
      }
    }

    synchronized {
      val currModCount = getManager.getModificationTracker.getModificationCount
      if (desugarizedType != null && desugarizedTypeModCount == currModCount) {
        return desugarizedType
      }
      desugarizedType = inner()
      desugarizedTypeModCount = currModCount
      return desugarizedType
    }
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    computeDesugarizedType match {
      case Some(typeElement) =>
        return typeElement.getType(TypingContext.empty)
      case _ =>
    }
    val tr = typeElement.getType(ctx)
    val res = tr.getOrElse(return tr)

    //todo: possible refactoring to remove parameterized type inference in simple type
    typeElement match {
      case s: ScSimpleTypeElement =>
        s.reference match {
          case Some(ref) =>
            if (ref.isConstructorReference) {
              ref.resolveNoConstructor match {
                case Array(ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
                  if to.isInstanceOf[PsiNamedElement] =>
                  return tr //all things were done in ScSimpleTypeElementImpl.innerType
                case Array(ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
                  if to.isInstanceOf[PsiNamedElement] =>
                  return tr //all things were done in ScSimpleTypeElementImpl.innerType
                case _ =>
              }
            }
            ref.bind() match {
              case Some(ScalaResolveResult(e: PsiMethod, _)) =>
                return tr //all things were done in ScSimpleTypeElementImpl.innerType
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }

    val args: scala.Seq[ScTypeElement] = typeArgList.typeArgs
    if (args.length == 0) return tr
    val argTypesWrapped = args.map {_.getType(ctx)}
    val argTypesgetOrElseped = argTypesWrapped.map {_.getOrAny}
    def fails(t: ScType) = (for (f@Failure(_, _) <- argTypesWrapped) yield f).foldLeft(Success(t, Some(this)))(_.apply(_))

    //Find cyclic type references
    argTypesWrapped.find(_.isCyclic) match {
      case Some(_) => fails(ScParameterizedType(res, Seq(argTypesgetOrElseped.toSeq: _*)))
      case None =>
        val typeArgs = args.map(_.getType(ctx))
        val result = ScParameterizedType(res, typeArgs.map(_.getOrAny))
        (for (f@Failure(_, _) <- typeArgs) yield f).foldLeft(Success(result, Some(this)))(_.apply(_))
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitParameterizedTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitParameterizedTypeElement(this)
      case _ => super.accept(visitor)
    }
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (ScalaPsiUtil.kindProjectorPluginEnabled(this)) {
      computeDesugarizedType match {
        case Some(tpe) =>
          val alias = tpe.depthFirst.filter(_.isInstanceOf[ScTypeAliasDefinition])
          for (a <- alias) {
            for (tp <- a.asInstanceOf[ScTypeAliasDefinition].typeParameters) {
              val text = tp.getText
              val lowerBound = text.indexOf(">:")
              val upperBound = text.indexOf("<:")
              //we have to call processor execute so both `+A` and A resolve: Lambda[`+A` => (A, A)]
              processor.execute(tp, state)
              processor.execute(new ScSyntheticClass(getManager, s"`$text`", Any), state)
              if (lowerBound < 0 && upperBound > 0) {
                processor.execute(new ScSyntheticClass(getManager, text.substring(0, upperBound), Any), state)
              } else if (upperBound < 0 && lowerBound > 0) {
                processor.execute(new ScSyntheticClass(getManager, text.substring(0, lowerBound), Any), state)
              } else if (upperBound > 0 && lowerBound > 0) {
                val actualText = text.substring(0, math.min(lowerBound, upperBound))
                processor.execute(new ScSyntheticClass(getManager, actualText, Any), state)
              }
            }
          }
        case _ =>
          val manager = getManager
          processor.execute(new ScSyntheticClass(manager, "+", Any), state)
          processor.execute(new ScSyntheticClass(manager, "-", Any), state)
      }
    }
    super.processDeclarations(processor, state, lastParent, place)
  }
}