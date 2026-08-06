object SCL5947 {

  trait HighlightErrorExampleComponent {
    val highlightError: HighlightErrorExample

    trait HighlightErrorExample
  }

  class ConcreteHighlightErrorExampleComponent extends HighlightErrorExampleComponent {
    val highlightError = new ConcreteHighlightErrorExample

    class ConcreteHighlightErrorExample extends HighlightErrorExample
  }

  class HighlightErrorExampleComponentUser(highlightError: HighlightErrorExampleComponent#HighlightErrorExample) {}

  trait HighlightErrorExampleMixed {
    self: HighlightErrorExampleComponent =>
    val errorExample = new HighlightErrorExampleComponentUser(/*start*/highlightError/*end*/)
  }
}

//HighlightErrorExampleMixed.this.HighlightErrorExample