trait Super {}
class Extender extends Super {}

val o: Object with Super = new Extender
// True