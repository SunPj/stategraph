package controllers

import domain.graph._
import javax.inject._
import play.api._
import play.api.libs.json.{JsString, Json, _}
import play.api.mvc._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.concurrent.ExecutionContext


@Singleton
class ResController @Inject()(val controllerComponents: ControllerComponents,
                              stateGraphService: StateGraphService)(implicit ec: ExecutionContext) extends BaseController {

  import ResController._

  def uploadGraphComponents() = Action(parse.json[GraphUploadRequest]) { implicit request =>
    val components = request.body.graph.components
      .map(c => c.id -> ComponentData(c.ownState,
        c.derivedState,
        c.checkStates,
        c.dependsOn.getOrElse(Set.empty),
        c.dependencyOf.getOrElse(Set.empty)))
      .toMap

    stateGraphService.uploadComponents(components)
    Ok
  }

  def getGraphComponents() = Action.async { implicit request: Request[AnyContent] =>
    stateGraphService.getGraphComponents().map { components =>
      val graphComponents = components.map {
        case (id, data) => GraphComponentData(id,
          data.ownState,
          data.derivedState,
          data.checkStates,
          if (data.dependants.nonEmpty) Some(data.dependants) else None,
          if (data.dependencies.nonEmpty) Some(data.dependencies) else None
        )
      }
      val g = Graph(graphComponents)
      Ok(Json.toJson(GraphSnapshot(g)))
    }
  }

  def processEvent() = Action(parse.json[ComponentCheckStateEvent]) { implicit request =>
    val event = request.body
    stateGraphService(event)
    Ok
  }
}

case class GraphUploadRequest(graph: Graph)
case class GraphSnapshot(graph: Graph)
case class Graph(components: Iterable[GraphComponentData])
case class GraphComponentData(id: String, ownState: ComponentState, derivedState: ComponentState,
                              checkStates: Map[String, ComponentState], dependencyOf: Option[Set[String]], dependsOn: Option[Set[String]])

object ResController {

  implicit val componentStateReads: Reads[ComponentState] = (json: JsValue) => {
    json.validate[JsString].flatMap {
      case JsString("no_data") => JsSuccess(NoData)
      case JsString("clear") => JsSuccess(Clear)
      case JsString("warning") => JsSuccess(Warning)
      case JsString("alert") => JsSuccess(Alert)
      case other => JsError(s"$other is non of the valid (no_data, clear, warning, alert) states")
    }
  }

  implicit val componentStateWrites: Writes[ComponentState] = {
    case NoData => JsString("no_data")
    case Clear => JsString("clear")
    case Warning => JsString("warning")
    case Alert => JsString("alert")
  }

  implicit val graphComponentDataReads: Format[GraphComponentData] = (
    (JsPath \ "id").format[String] and
      (JsPath \ "own_state").format[ComponentState] and
      (JsPath \ "derived_state").format[ComponentState] and
      (JsPath \ "check_states").format[Map[String, ComponentState]] and
      (JsPath \ "dependency_of").formatNullable[Set[String]] and
      (JsPath \ "depends_on").formatNullable[Set[String]]
  )(GraphComponentData.apply, unlift(GraphComponentData.unapply))

  implicit val graphFormat: Format[Graph] = Json.format[Graph]
  implicit val graphSnapshotWrites: Writes[GraphSnapshot] = Json.writes[GraphSnapshot]
  implicit val graphUploadRequestReads: Reads[GraphUploadRequest] = Json.reads[GraphUploadRequest]

  // TODO use something nicer instead of _.toLong
  implicit val componentCheckStateEventReads: Reads[ComponentCheckStateEvent] = (
    (JsPath \ "timestamp").read[String].map(_.toLong) and
      (JsPath \ "component").read[String] and
      (JsPath \ "check_state").read[String] and
      (JsPath \ "state").read[ComponentState]
  )(ComponentCheckStateEvent.apply _)
}
