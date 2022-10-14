public class A {
    public static void main(String[] args) {
        /*start*/Runnable r = (Runnable & Serializable)() -> System.out.println("Serializable!");/*end*/
    }
}
/*
val r: Runnable = () => System.out.println("Serializable!").asInstanceOf[Runnable with Nothing]
 */