import com.intellij.psi.PsiElement
import java.util

class Z[T]
val x: util.Collection[Z[_ <: PsiElement]] = null

object L {
  def foo(x: util.Collection[Z[_ <: PsiElement]]) = 1
  def foo(x: Int) = false
}

/*start*/L.foo(x)/*end*/
//Int