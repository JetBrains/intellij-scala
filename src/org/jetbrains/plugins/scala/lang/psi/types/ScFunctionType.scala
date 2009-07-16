package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiClass

/**
* @author ilyas
*/

case class ScFunctionType(returnType: ScType, params: Seq[ScType]) extends ScType {
  override def equiv(that : ScType) = that match {
    case ScFunctionType(rt1, params1) => (returnType equiv rt1) &&
                               (params.zip(params1) forall {case (x,y) => x equiv y})
    case ScParameterizedType(ScDesignatorType(c : PsiClass), args)
      if args.length > 0 && c.getQualifiedName == "scala.Function" + (args.length -1) => {
      (returnType equiv args(args.length -1)) && args.slice(0, args.length - 1).zip(params.toArray).forall{case (t1, t2) => t1 equiv t2}
    }
    case _ => false
  }

  override def hashCode: Int = {
    returnType.hashCode + params.hashCode * 31
  }
}

case class ScTupleType(components: Seq[ScType]) extends ScType {
  override def equiv(that : ScType) = that match {
    case ScTupleType(c1) => components.zip(c1) forall {case (x,y)=> x equiv y}
    case ScParameterizedType(ScDesignatorType(c : PsiClass), args)
      if args.length > 0 && c.getQualifiedName == "scala.Tuple" + args.length => {
      args.zip(components.toArray).forall{case (t1, t2) => t1 equiv t2}
    }
    case _ => false
  }
}