import scala.Option;

public enum TestEnum {
    A,
    B,
    C;

    public static Option<TestEnum> unapply(String s) {
        try {
            return Option.apply(TestEnum.valueOf(s));
        } catch (IllegalArgumentException e) {
            return Option.apply(null);
        }
    }
}