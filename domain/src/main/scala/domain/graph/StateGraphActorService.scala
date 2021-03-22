package domain.graph

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import com.google.inject.Inject
import domain.graph.StateGraphActor.{ApplyEvent, GetGraphComponents, UploadComponents}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * StateGraphService actor based implementation
  *
  * @param stateGraphActor stateGraphActor
  */
class StateGraphActorService @Inject()(stateGraphActor: ActorRef[StateGraphActor.Command])(implicit scheduler: Scheduler) extends StateGraphService {
  /**
    * Uploads components into a graph
    *
    * Note! When called multiple times this will either add new components and dependencies to the graph or
    * update the existing ones by matching their IDs.
    *
    * @param data seq of component data to be added to a graph
    * @return
    */
  override def uploadComponents(data: Map[String, ComponentData]): Future[Unit] = {
    stateGraphActor ! UploadComponents(data)
    Future.successful(())
  }

  /**
    * Applies given event to a graph
    *
    * @param event component checkState event
    * @return
    */
  override def apply(event: ComponentCheckStateEvent): Future[Unit] = {
    stateGraphActor ! ApplyEvent(event)
    Future.successful(())
  }

  /**
    * @return the list of graph components
    */
  override def getGraphComponents(): Future[Map[String, ComponentData]] = {
    implicit val timeout: Timeout = 3.seconds
    stateGraphActor ? GetGraphComponents
  }
}
