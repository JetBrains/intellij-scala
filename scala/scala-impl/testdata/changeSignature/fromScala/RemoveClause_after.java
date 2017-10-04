public class RemoveClause extends RemoveClauseConstructor {

    public RemoveClause(int i, boolean b) {
        super(b, i);
    }

    public void foo() {
        RemoveClauseConstructor c = new RemoveClauseConstructor(true, 1);
    }
}
