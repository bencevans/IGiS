package igis.app.controllers

import binding.git.GitCommit
import eu.devtty.cid.CID
import igis.mvc.{Controller, Node, Request, Response}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success
import igis.util.HtmlDom._
import igis.util.TimeUtil
import org.scalajs.dom.raw.HTMLLinkElement

class CommitsController extends Controller {
  lazy val template = html.commits().template

  def fetchCommits(rootCid: String, depth: Int, node: Node): Future[Seq[(CID, GitCommit)]] = {
    node.ipfs.dag.get(rootCid).flatMap{ res =>
      val commit = res.value.asInstanceOf[GitCommit]

      commit.parents.take(depth) //get at most [depth] parents
        .map(p => fetchCommits(p.cid().toBaseEncodedString(), depth - commit.parents.length, node)) // fetch their subtrees
        .fold(Future.successful(Seq((new CID(rootCid), commit)))){ case (a, b) => a.flatMap(as => b.map(bs => as ++ bs))} //merge subtrees
    }
  }

  def apply(req: Request): Future[Response] = {
    val builder = template.builder
    val parts = req.remPath.split("/")
    val repo = parts.head
    val at = if(parts.length > 1) parts(1) else parts.head

    fetchCommits(at, 10, req.node).andThen{
      case Success(commits) =>
        builder.setMultiple[(CID, GitCommit)]("commits-root", commits, { case(b, (cid, commit)) =>
          b.set("commit-message", commit.message.lines.next())
          b.set("commit-author", commit.author.name)
          b.set("commit-date", TimeUtil.gitTimeToDate(commit.author.date).toDateString())
          b.modElement("commit-link", _.asInstanceOf[HTMLLinkElement].href = s"#/commit/${cid.toBaseEncodedString()}")
        })

        //TODO: this is terrible, will lose subtrees
        builder.modElement("commit-older", _.asInstanceOf[HTMLLinkElement].href = s"#/repo/commits/$repo/${commits.last._2.parents(0).cid().toBaseEncodedString()}")
    }

    Future.successful(Response.withElement(builder.element))
  }
}
