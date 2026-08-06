val a = /*resolved: true, applicable: true*/ s"blah blah ${i}"
val b = /*resolved: true, applicable: true*/ f"blah ${a}%s blah"
val c = /*resolved: true, applicable: true*/ s"""blah blah $b blah"""
val d = /*resolved: true, applicable: true*/ f"""blah ${c}%s blah"""


