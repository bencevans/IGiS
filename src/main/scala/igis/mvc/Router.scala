package igis.mvc

import scala.concurrent.Future

class Router extends Controller {
  var children: Map[String, Router] = Map[String, Router]()
  var default: Option[Controller] = Option.empty

  def this(default: Controller) {
    this()
    this.default = Some(default)
  }

  def register(controller: Controller, path: String): Unit = {
    val parts = path.replaceFirst("^/*", "").split("/")
    if(parts.isEmpty || path == "") {
      default = Some(controller)
      return
    }

    children.get(parts.head) match {
      case None =>
        val router = new Router()
        router.register(controller, parts.drop(1).mkString("/"))
        children += parts.head -> router
      case Some(router) =>
        router.register(controller, parts.drop(1).mkString("/"))
    }
  }

  def apply(req: Request): Future[Response] = {
    val parts = req.remPath.replaceFirst("^/*", "").split("/")
    if(parts.isEmpty || req.remPath == "") {
      default match {
        case None => Future.successful(Response.withData("404.1"))
        case Some(d) => d(req)
      }
    } else {
      children.get(parts.head) match {
        case None =>
          default match {
            case None => Future.successful(Response.withData("404.2"))
            case Some(d) => d(req)
          }
        case Some(cont) => cont(new Request(req, req.node))
      }
    }
  }
}
