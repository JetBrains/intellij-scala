package org.jetbrains.plugins.scala
package lang.resolve.processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}

import scala.collection.Set

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.04.2010
 */
class ConstructorResolveProcessor(constr: PsiElement, refName: String, args: List[Seq[Expression]],
                                  typeArgs: Seq[ScTypeElement], kinds: Set[ResolveTargets.Value],
                                  shapeResolve: Boolean, allConstructors: Boolean)
                                 (implicit override val typeSystem: TypeSystem)
        extends MethodResolveProcessor(constr, refName, args, typeArgs, Seq.empty, kinds,
          isShapeResolve = shapeResolve, enableTupling = true) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val fromType = getFromType(state)
    val subst = fromType match {
      case Some(tp) => getSubst(state).followUpdateThisType(tp)
      case _ => getSubst(state)
    }

    def nameShadow0: Option[String] = Option(state.get(ResolverEnv.nameKey))
    if (nameAndKindMatch(named, state)) {
      val accessible = isAccessible(named, ref)
      if (accessibility && !accessible) return true
      named match {
        case clazz: PsiClass =>
          val constructors: Array[PsiMethod] =
            if (accessibility) clazz.constructors.filter(isAccessible(_, ref))
            else clazz.constructors
          if (constructors.isEmpty) {
            //this is for Traits for example. They can be in constructor position.
            // But they haven't constructors.
            addResult(new ScalaResolveResult(clazz, subst, getImports(state), nameShadow0, boundClass = getBoundClass(state),
              fromType = fromType, isAccessible = accessible))
          }
          else {
            addResults(constructors.toSeq.map(constr => new ScalaResolveResult(constr, subst, getImports(state), nameShadow0,
              parentElement = Some(clazz), boundClass = getBoundClass(state), fromType = fromType,
              isAccessible = isAccessible(constr, ref) && accessible
            )))
          }
        case ta: ScTypeAliasDeclaration =>
          addResult(new ScalaResolveResult(ta, subst, getImports(state), nameShadow0, boundClass = getBoundClass(state),
            fromType = fromType, isAccessible = accessible))
        case ta: ScTypeAliasDefinition =>
          lazy val r = new ScalaResolveResult(ta, subst, getImports(state), nameShadow0, boundClass = getBoundClass(state), fromType = fromType, isAccessible = true)
          val tp = ta.aliasedType(TypingContext.empty).getOrElse({
            addResult(r)
            return true
          })
          ScType.extractClassType(tp) match {
            case Some((clazz, s)) =>
              val constructors: Array[PsiMethod] =
                if (accessibility) clazz.constructors.filter(isAccessible(_, ref))
                else clazz.constructors
              if (constructors.isEmpty) addResult(r)
              else {
                addResults(constructors.toSeq.map(constr => new ScalaResolveResult(constr, subst.followed(s), getImports(state),
                    nameShadow0, parentElement = Some(ta), boundClass = getBoundClass(state), fromType = fromType,
                    isAccessible = isAccessible(constr, ref) && accessible
                )))
              }
            case _ =>
              addResult(r)
          }
        case _ =>
      }
    }
    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    if (!allConstructors) {
      val superCandidates = super.candidatesS
      if (superCandidates.size <= 1) superCandidates
      else {
        superCandidates.map(constr => new ScalaResolveResult(constr.getActualElement, constr.substitutor,
          constr.importsUsed, boundClass = constr.boundClass, fromType = constr.fromType,
          isAccessible = constr.isAccessible))
      }
    } else {
      super.candidatesS
    }
  }
}