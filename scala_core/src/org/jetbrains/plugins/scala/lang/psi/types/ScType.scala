package org.jetbrains.plugins.scala.lang.psi.types

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import api.statements.ScTypeAlias
import api.toplevel.typedef.ScTypeDefinition
import impl.ScalaPsiManager
import resolve.ScalaResolveResult
import com.intellij.psi._
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

trait ScType {

  def equiv(t: ScType): Boolean = t == this

  sealed def conforms(t: ScType): Boolean = Conformance.conforms(this, t)

  override def toString = ScType.presentableText(this)
}

abstract case class StdType(val name : String, val tSuper : Option[StdType]) extends ScType {
  def asClass(project : Project) = SyntheticClasses.get(project).byName(name) match {
    case Some(c) => c
  }
}

case object Any extends StdType("Any", None)

case object Null extends StdType("Null", Some(AnyRef))

case object AnyRef extends StdType("AnyRef", Some(Any))

case object Nothing extends StdType("Nothing", None)

case object Singleton extends StdType("Singleton", Some(AnyRef))

case object AnyVal extends StdType("AnyVal", Some(Any))

abstract case class ValType(override val name : String) extends StdType(name, Some(AnyVal))

object Unit extends ValType("Unit")
object Boolean extends ValType("Boolean")
object Char extends ValType("Char")
object Int extends ValType("Int")
object Long extends ValType("Long")
object Float extends ValType("Float")
object Double extends ValType("Double")
object Byte extends ValType("Byte")
object Short extends ValType("Float")

object ScType {
  def create(psiType : PsiType, project : Project) : ScType = psiType match {
    case classType : PsiClassType => {
      val result = classType.resolveGenerics
      result.getElement match {
        case tp : PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
        case clazz if clazz != null => {
          val tps = clazz.getTypeParameters
          val des = new ScDesignatorType(clazz)
          tps match {
            case Array() => des
            case _ => new ScParameterizedType(des, tps.map
                      {tp => create(result.getSubstitutor.substitute(tp), project)})
          }
        }
        case _ => Nothing
      }
    }
    case arrayType : PsiArrayType => {
      val arrayClass = JavaPsiFacade.getInstance(project).findClass("scala.Array", GlobalSearchScope.allScope(project))
      if (arrayClass != null) {
        val tps = arrayClass.getTypeParameters
        if (tps.length == 1) {
          val typeArg = create(arrayType.getComponentType, project)
          new ScParameterizedType(new ScDesignatorType(arrayClass), Array(typeArg))
        } else new ScDesignatorType(arrayClass)
      } else Nothing
    }

    case PsiType.VOID => Unit
    case PsiType.BOOLEAN => Boolean
    case PsiType.CHAR => Char
    case PsiType.INT => Int
    case PsiType.LONG => Long
    case PsiType.FLOAT => Float
    case PsiType.DOUBLE => Double
    case PsiType.BYTE => Byte
    case PsiType.SHORT => Short
    case PsiType.NULL => Null
    case wild : PsiWildcardType => new ScExistentialArgument("_", Nil,
      create(wild.getSuperBound, project), create(wild.getExtendsBound, project))
    case capture : PsiCapturedWildcardType => new ScTypeVariable("_", Nil,
      create(capture.getLowerBound, project), create(capture.getUpperBound, project))
    case null => new ScExistentialArgument("_", Nil, Nothing, Any) // raw type argument from java
    case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
  }

  def toPsi(t : ScType, project : Project, scope : GlobalSearchScope) : PsiType = t match {
    case Unit => PsiType.VOID
    case Boolean => PsiType.BOOLEAN
    case Char => PsiType.CHAR
    case Int => PsiType.INT
    case Long => PsiType.LONG
    case Float => PsiType.FLOAT
    case Double => PsiType.DOUBLE
    case Byte => PsiType.BYTE
    case Short => PsiType.SHORT
    case Null => PsiType.NULL
    case ScDesignatorType(c : PsiClass) => JavaPsiFacade.getInstance(project).getElementFactory.createType(c, PsiSubstitutor.EMPTY)
    case ScParameterizedType(ScDesignatorType(c : PsiClass), args) =>
      if (c.getQualifiedName == "scala.Array" && args.length == 1)
        new PsiArrayType(toPsi(args(0), project, scope))
      else {
        val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                  {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope))}
        JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
      }
    case _ => JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName("java.lang.Object", scope)
  }

  def extractClassType(t : ScType) : Option[Pair[PsiClass, ScSubstitutor]] = t match {
    case ScDesignatorType(clazz : PsiClass) => Some(clazz, ScSubstitutor.empty)
    case proj@ScProjectionType(p, _) => proj.resolveResult match {
      case Some(ScalaResolveResult(c: PsiClass, s)) => Some((c, s))
      case None => None
    }
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((c, s)) => Some((c, s.followed(p.substitutor)))
        case None => None
      }
    }
    case _ => None
  }

  def presentableText(t : ScType) = typeText(t, {e => e.getName})

  //todo: resolve cases when java type have keywords as name (type -> `type`)
  def canonicalText(t : ScType) = typeText(t,
    {e => e match {
      case c : PsiClass => {
        val qname = c.getQualifiedName
        if (qname != null) "_root_" + qname else c.getName
      }
      case p : PsiPackage => "_root_" + p.getQualifiedName
      case _ => e.getName
    }
  })

  private def typeText(t : ScType, nameFun : PsiNamedElement => String): String = {
    val buffer = new StringBuilder
    def appendSeq(ts : Seq[ScType], sep : String) = {
      var first = true
      for (t <- ts) {
        if (!first) buffer.append(sep)
        first = false
        inner(t)
      }
    }
    def inner(t : ScType) : Unit = t match {
      case StdType(name, _) => buffer.append(name)
      case ScFunctionType(ret, params) => buffer.append("("); appendSeq(params, ", "); buffer.append(") =>"); inner(ret)
      case ScTupleType(comps) => buffer.append("("); appendSeq(comps, ", "); buffer.append(")")
      case ScDesignatorType(e) => buffer.append(nameFun(e))
      case ScProjectionType(p, name) => inner(p); buffer.append("#").append(name)
      case ScParameterizedType (des, typeArgs) => inner(des); buffer.append("["); appendSeq(typeArgs, ","); buffer.append("]")
      case ScTypeVariable(name, _, _, _) => buffer.append(name)
      case ScTypeAliasType(name, _, _, _) => buffer.append(name)
      case ScExistentialArgument(name, args, lower, upper) => {
        buffer.append(name)
        if (args.length > 0) {
          buffer.append("[");
          appendSeq(args, ",")
          buffer.append("]")
        }
        if (lower != Null) {
          buffer.append(" >: ")
          inner(lower)
        }
        upper match {
          case ScDesignatorType(e: PsiClass) if e.getQualifiedName == "java.lang.Object" || e.getQualifiedName == "scala.ScalaObject" =>
          case _ =>
            buffer.append(name).append(" <: ")
            inner(upper)
        }
      }
      case ScExistentialType(q, wilds) => {
        inner(q)
        buffer.append(" forSome{"); appendSeq(wilds, "; "); buffer.append("}")
      }
      case _ => null //todo
    }
    inner(t)
    buffer.toString
  }
}