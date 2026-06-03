package p {
import java.util.ArrayList

class Container {
	type t = java.lang.Double

	def m(p: java.lang.Float) {}		//(1)
	def m(p: java.lang.Object) {}		//(2)
	def m(p: t) {}				//(3)
	def m(p: Int) {}			//(4)

	/* line: 7 */m(new java.lang.Float(1f))		// resolved to (1) and (2)
	/* line: 8 */m(new java.lang.Object)			// resolved to (1), (2) and (3)
	/* line: 9 */m(new t(1d))				// resolved to (1) and (2)
	/* line: 10 */m(1)					// ok
	/* line: 8 */m(new ArrayList[java.lang.Integer](5))	// ok
}
}