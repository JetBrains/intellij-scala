public class AddNewClauseJava extends AddNewClause {
    @Override
    public void foo(boolean b, int x, int y) {
        AddNewClause a = new AddNewClause();
        a.foo(true, 1, 0);


        super.foo(true, x, 0);
    }
}
