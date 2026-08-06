class PlayController extends Controller{

  def index = /*start*/Cached(_ => "index", duration = 86400)/*end*/ {
    Action { implicit request =>
      Ok(views.html.index.render("HelloWord"))
    }
  }
}