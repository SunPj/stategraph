package domain.graph

import java.time.Instant

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import domain.graph.ComponentActor.{SentSnapshot, Update, UpdateCheckState}

/**
  * Graph of components that supports following actions
  * - upload components
  * - send an event to a component
  * - return a list of components
  */
object StateGraphActor {
  sealed trait Command

  sealed trait EventCommand extends Command
  case object EventConsumed extends EventCommand
  case class EventPropagated(nodesCount: Int) extends EventCommand

  case class UploadComponents(data: Map[String, ComponentData]) extends Command
  case class ApplyEvent(event: ComponentCheckStateEvent) extends Command
  case class GetGraphComponents(replyTo: ActorRef[Map[String, ComponentData]]) extends Command
  case class ComponentSnapshot() extends Command

  def apply(): Behavior[Command] = Behaviors.withStash(100) { buffer =>
    Behaviors.setup[Command] { context =>
      new GraphActor(context, buffer).start()
    }
  }

  private case class Graph(components: Map[String, ActorRef[ComponentActor.Command]]) {
    def addComponents(components: Map[String, ActorRef[ComponentActor.Command]]) = Graph(this.components ++ components)

    def getComponent(componentId: String): Option[ActorRef[ComponentActor.Command]] = components.get(componentId)

    def getComponents(componentIds: Set[String]): Map[String, ActorRef[ComponentActor.Command]] = components.filterKeys(componentIds.contains)

    lazy val getComponentActors: Iterable[ActorRef[ComponentActor.Command]] = components.values

    lazy val componentIds: Iterable[String] = components.keys

    def hasNoComponent(id: String): Boolean = !(components contains id)
  }

  /**
    * Actor based graph implementation which is in either two states Active or Stabilizing,
    * while active state processes graph commands and switches to Stabilizing state to wait till graph state is
    * steady
    *
    * @param context actor context
    * @param buffer  buffer
    */
  private class GraphActor(context: ActorContext[Command], buffer: StashBuffer[Command]) {

    def start(): Behavior[Command] = active(Graph(Map.empty))

    /**
      * Active behaviour which processes incoming command
      *
      * @param graph graph
      * @return
      */
    private def active(graph: Graph): Behavior[Command] = {
      /**
        * Applies event to the graph
        *
        * @param event  event
        * @return
        */
      def applyEvent(event: ComponentCheckStateEvent): Behavior[Command] = {
        graph.getComponent(event.componentId) match {
          case Some(ref) =>
            ref ! UpdateCheckState(event.checkStateId, event.checkStateState, event.timestamp)
            stabilizing(graph, 1)
          case None =>
            context.log.warn(s"No component has been found by id = ${event.componentId}")
            Behaviors.same
        }
      }

      /**
        * Uploads components to the graph
        *
        * @param components graph components to be uploaded to graph
        * @return
        */
      def uploadComponents(components: Map[String, ComponentData]): Behavior[Command] = {
        val newComponents = components.filterKeys(graph.hasNoComponent).map {
          case (componentId, componentData) =>
            componentId -> context.spawn(ComponentActor(context.self, componentId, componentData.checkStates), componentId)
        }

        val newGraph = graph.addComponents(newComponents)

        components.foreach {
          case (componentId, componentData) =>
            newGraph.getComponent(componentId) match {
              case Some(ref) =>
                val dependants = newGraph.getComponents(componentData.dependants)
                ref ! Update(componentData.checkStates, dependants, componentData.dependencies)
              case None =>
                context.log.warn(s"No component has been found by id = $componentId")
            }
        }

        stabilizing(newGraph, components.size)
      }

      /**
        * Takes the snapshot of graph components and send it to replyTo actor
        *
        * @param replyTo actor that the snapshot should be sent to
        * @return
        */
      def getGraphComponents(replyTo: ActorRef[Map[String, ComponentData]]): Behavior[Command] = {
        if (graph.components.isEmpty) {
          replyTo ! Map.empty
        } else {
          // TODO I should think of using something else
          val time = Instant.now().toEpochMilli
          val snapshotActor = context.spawn(SnapshotActor(graph.components.keySet, replyTo), s"snapshot-$time")
          graph.getComponentActors.foreach(_ ! SentSnapshot(snapshotActor))
        }

        Behaviors.same
      }

      Behaviors.receiveMessage {
        case ApplyEvent(event: ComponentCheckStateEvent) =>
          applyEvent(event)

        case UploadComponents(componentsData) =>
          uploadComponents(componentsData)

        case GetGraphComponents(replyTo) =>
          getGraphComponents(replyTo)
      }
    }

    /**
      * Stabilizing behaviour which waits till all events are propagated and graph is steady
      *
      * @param graph       graph
      * @param awaitEvents number of events waining for being processed
      * @return
      */
    private def stabilizing(graph: Graph, awaitEvents: Int): Behavior[Command] = Behaviors.receiveMessage {
      case EventConsumed =>
        if (awaitEvents - 1 == 0)
          buffer.unstashAll(active(graph))
        else
          stabilizing(graph, awaitEvents - 1)

      case EventPropagated(nodesCount) =>
        stabilizing(graph, awaitEvents + nodesCount)

      case other =>
        buffer.stash(other)
        Behaviors.same
    }
  }

}
