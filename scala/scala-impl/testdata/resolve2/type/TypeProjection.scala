class Outer {
        class Inner {}
}

object O {
 def createInner: Q.type#/* line: 2 */Inner forSome {val Q: Outer} = null
}