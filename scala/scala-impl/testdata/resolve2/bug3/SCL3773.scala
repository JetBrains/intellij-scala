trait A {
  type R = Int
}
def fop[R](parameter: A): parameter./*resolved: true*/R = 0

def fop[R](parameter: A): parameter./*resolved: true*/R