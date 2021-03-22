package domain.graph

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

/**
  * Scope actor that composes response from all actors and replies to caller
  */
private[graph] object SnapshotActor {

  def apply(components: Set[String], replyTo: ActorRef[Map[String, ComponentData]]) = Behaviors.setup[(String, ComponentData)] { context =>
    var componentSnapshotsToWait: Set[String] = components
    var componentSnapshots: Map[String, ComponentData] = Map.empty

    def nextBehavior(): Behavior[(String, ComponentData)] = {
      if (componentSnapshotsToWait.isEmpty) {
        replyTo ! componentSnapshots
        Behaviors.stopped
      } else {
        Behaviors.same
      }
    }

    Behaviors.receiveMessage {
      case (id: String, componentData: ComponentData) =>
        componentSnapshotsToWait -= id
        componentSnapshots += (id -> componentData)
        nextBehavior()
      case _ =>
        Behaviors.unhandled
    }
  }
}
