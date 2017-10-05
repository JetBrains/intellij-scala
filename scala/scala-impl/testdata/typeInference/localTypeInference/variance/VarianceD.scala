class Contra[-_]
class Cov[+_]
trait A
trait B extends A
trait C extends B
def contra[a](): Contra[a] = null
def contraLB[a >: C](): Contra[a] = null
def contraLBUB[a >: C <: A](): Contra[a] = null
def contraUB[a <: A](): Contra[a] = null
/*start*/contraLB()/*end*/
//Contra[C]