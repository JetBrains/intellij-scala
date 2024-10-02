class P1; object P1 extends P1
class P2; object P2 extends P2
class S1; object S1 extends S1
class S2; object S2 extends S2

trait GrandFather[A] {
  type B = A

  def putA(x: A): Unit
  def putB(x: B): Unit

  trait Father[M] {
    type C = A
    type D = B
    type N = M

    def putA2(x: A): Unit
    def putB2(x: B): Unit
    def putC(x: C): Unit
    def putD(x: D): Unit
    def setM(x: M): Unit
    def setN(x: N): Unit

    trait Son {
      type E = A
      type F = B
      type G = C
      type H = D
      type O = M
      type P = N

      def putA3(x: A): Unit
      def putB3(x: B): Unit
      def putC2(x: C): Unit
      def putD2(x: D): Unit
      def putE(x: E): Unit
      def putF(x: F): Unit
      def putG(x: G): Unit
      def putH(x: H): Unit
      def setM2(x: M): Unit
      def setN2(x: N): Unit
      def setO(x: O): Unit
      def setP(x: P): Unit
    }
  }
}

trait Phillip extends GrandFather[P1] {
  def aA()   = this./* resolved: true */putA(P1)
  def aB()   = this./* resolved: true */putB(P1)

  trait Charles extends GrandFather[P2] with Father[S1] {
    def bA()      =         this./* resolved: true */putA(P2)
    def bB()      =         this./* resolved: true */putB(P2)

    def bA_1()    = Phillip.this./* resolved: true */putA(P1)
    def bB_1()    = Phillip.this./* resolved: true */putB(P1)

    def bA2()     =         this./* resolved: true */putA2(P1)
    def bB2()     =         this./* resolved: true */putB2(P1)
    def bC()      =         this./* resolved: true */putC(P1)
    def bD()      =         this./* resolved: true */putD(P1)
    def bM()      =         this./* resolved: true */setM(S1)
    def bN()      =         this./* resolved: true */setN(S1)

    trait William extends Father[S2] with Son {
      def cA_1()  = Charles.this./* resolved: true */putA(P2)
      def cA_2()  = Phillip.this./* resolved: true */putA(P1)
      def cB_1()  = Charles.this./* resolved: true */putB(P2)
      def cB_2()  = Phillip.this./* resolved: true */putB(P1)

      def cA2()   =         this./* resolved: true */putA2(P2)
      def cB2()   =         this./* resolved: true */putB2(P2)
      def cC()    =         this./* resolved: true */putC(P2)
      def cD()    =         this./* resolved: true */putD(P2)
      def cM()    =         this./* resolved: true */setM(S2)
      def cN()    =         this./* resolved: true */setN(S2)

      def cA2_1() = Charles.this./* resolved: true */putA2(P1)
      def cB2_1() = Charles.this./* resolved: true */putB2(P1)
      def cC_1()  = Charles.this./* resolved: true */putC(P1)
      def cD_1()  = Charles.this./* resolved: true */putD(P1)
      def cM_1()  = Charles.this./* resolved: true */setM(S1)
      def cN_1()  = Charles.this./* resolved: true */setN(S1)

      def cA3()   =         this./* resolved: true */putA3(P1)
      def cB3()   =         this./* resolved: true */putB3(P1)
      def cC2()   =         this./* resolved: true */putC2(P1)
      def cD2()   =         this./* resolved: true */putD2(P1)
      def cE()    =         this./* resolved: true */putE(P1)
      def cF()    =         this./* resolved: true */putF(P1)
      def cG()    =         this./* resolved: true */putG(P1)
      def cH()    =         this./* resolved: true */putH(P1)
      def cM2()   =         this./* resolved: true */setM2(S1)
      def cN2()   =         this./* resolved: true */setN2(S1)
      def cO()    =         this./* resolved: true */setO(S1)
      def cP()    =         this./* resolved: true */setP(S1)

      trait George extends Son {
        def dA_2()  = Charles.this./* resolved: true */putA(P2)
        def dA_3()  = Phillip.this./* resolved: true */putA(P1)
        def dB_2()  = Charles.this./* resolved: true */putB(P2)
        def dB_3()  = Phillip.this./* resolved: true */putB(P1)

        def dA2_1() = William.this./* resolved: true */putA2(P2)
        def dA2_2() = Charles.this./* resolved: true */putA2(P1)
        def dB2_1() = William.this./* resolved: true */putB2(P2)
        def dB2_2() = Charles.this./* resolved: true */putB2(P1)
        def dC_1()  = William.this./* resolved: true */putC(P2)
        def dC_2()  = Charles.this./* resolved: true */putC(P1)
        def dD_1()  = William.this./* resolved: true */putD(P2)
        def dD_2()  = Charles.this./* resolved: true */putD(P1)
        def dM_1()  = William.this./* resolved: true */setM(S2)
        def dM_2()  = Charles.this./* resolved: true */setM(S1)
        def dN_1()  = William.this./* resolved: true */setN(S2)
        def dN_2()  = Charles.this./* resolved: true */setN(S1)

        def dA3()   =         this./* resolved: true */putA3(P2)
        def dB3()   =         this./* resolved: true */putB3(P2)
        def dC2()   =         this./* resolved: true */putC2(P2)
        def dD2()   =         this./* resolved: true */putD2(P2)
        def dE()    =         this./* resolved: true */putE(P2)
        def dF()    =         this./* resolved: true */putF(P2)
        def dG()    =         this./* resolved: true */putG(P2)
        def dH()    =         this./* resolved: true */putH(P2)
        def dM2()   =         this./* resolved: true */setM2(S2)
        def dN2()   =         this./* resolved: true */setN2(S2)
        def dO()    =         this./* resolved: true */setO(S2)
        def dP()    =         this./* resolved: true */setP(S2)

        def dA3_1() = William.this./* resolved: true */putA3(P1)
        def dB3_1() = William.this./* resolved: true */putB3(P1)
        def dC2_1() = William.this./* resolved: true */putC2(P1)
        def dD2_1() = William.this./* resolved: true */putD2(P1)
        def dE_1()  = William.this./* resolved: true */putE(P1)
        def dF_1()  = William.this./* resolved: true */putF(P1)
        def dG_1()  = William.this./* resolved: true */putG(P1)
        def dH_1()  = William.this./* resolved: true */putH(P1)
        def dM2_1() = William.this./* resolved: true */setM2(S1)
        def dN2_1() = William.this./* resolved: true */setN2(S1)
        def dO_1()  = William.this./* resolved: true */setO(S1)
        def dP_1()  = William.this./* resolved: true */setP(S1)
      }
    }
  }
}
