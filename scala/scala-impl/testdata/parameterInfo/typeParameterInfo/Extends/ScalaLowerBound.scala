class Y
class H extends Y
class ScalaLowerBound[t >: H]

new ScalaLowerBound[Y<caret>]
//TEXT: t >: H, STRIKEOUT: false