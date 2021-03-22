package domain.graph

import scala.concurrent.Future
import scala.language.higherKinds

/**
  * Provides the contract to operate with Graph
  */
trait StateGraphService {
  /**
    * Uploads components into a graph
    *
    * Note! When called multiple times this will either add new components and dependencies to the graph or
    * update the existing ones by matching their IDs.
    *
    * @param data seq of component data to be added to a graph
    * @return
    */
  def uploadComponents(data: Map[String, ComponentData]): Future[Unit]

  /**
    * Applies given event to a graph
    *
    * @param event component checkState event
    * @return
    */
  def apply(event: ComponentCheckStateEvent): Future[Unit]

  /**
    * @return the list of graph components
    */
  def getGraphComponents(): Future[Map[String, ComponentData]]
}

sealed trait ComponentState
case object NoData extends ComponentState
case object Clear extends ComponentState
case object Warning extends ComponentState
case object Alert extends ComponentState

object ComponentState {
  implicit val ordering = Ordering.by[ComponentState, Int]{
    case NoData => 0
    case Clear => 1
    case Warning => 2
    case Alert => 3
  }
}

case class ComponentData(ownState: ComponentState,
                         derivedState: ComponentState,
                         checkStates: Map[String, ComponentState],
                         dependencies: Set[String],
                         dependants: Set[String])

case class ComponentCheckStateEvent(timestamp: Long, componentId: String, checkStateId: String, checkStateState: ComponentState)
