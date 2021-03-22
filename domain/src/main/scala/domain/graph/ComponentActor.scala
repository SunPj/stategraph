package domain.graph

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import domain.graph.StateGraphActor.{EventCommand, EventConsumed, EventPropagated}

private[graph] object ComponentActor {

  sealed trait Command

  case class Update(checkStates: Map[String, ComponentState], dependants: Map[String, ActorRef[ComponentActor.Command]], dependencies: Set[String]) extends Command

  case class UpdateCheckState(checkStateId: String, checkStateState: ComponentState, timestamp: Long) extends Command

  case class ComponentUpdatedEvent(id: String, ownState: ComponentState) extends Command

  case class SentSnapshot(replyTo: ActorRef[(String, ComponentData)]) extends Command

  def apply(supervisor: ActorRef[EventCommand], id: String, checkStates: Map[String, ComponentState]): Behavior[ComponentActor.Command] =
    Behaviors.setup(ctx => new ComponentActor(supervisor, id, ctx).behaviour(State(checkStates, Map.empty, Map.empty)))

  private case class State(checkStates: Map[String, ComponentState],
                           dependants: Map[String, ActorRef[ComponentActor.Command]],
                           dependencyDerivedStates: Map[String, ComponentState]) {

    lazy val ownState: ComponentState = {
      checkStates.values.toList.sorted(ComponentState.ordering.reverse).headOption.getOrElse(NoData)
    }

    lazy val derivedState: ComponentState = {
      (ownState :: dependencyDerivedStates.values.toList).sorted(ComponentState.ordering.reverse).headOption.getOrElse(NoData)
    }

  }

  private class ComponentActor(supervisor: ActorRef[EventCommand],
                               id: String,
                               context: ActorContext[ComponentActor.Command]) {

    private def propagateStateIfDerivedStateChanged(state: State, newState: State): Behavior[Command] = {
      if (state.derivedState != newState.derivedState) {
        state.dependants.values.foreach(_ ! ComponentUpdatedEvent(id, newState.derivedState))
        supervisor ! EventPropagated(newState.dependants.size)
      }
      supervisor ! EventConsumed
      behaviour(newState)
    }

    def behaviour(state: State): Behavior[Command] = Behaviors.receiveMessagePartial {
      // updates component
      case Update(newCheckStates, newDependants, newDependencies) =>
        val newState = state.copy(checkStates = newCheckStates, dependants = newDependants, dependencyDerivedStates = newDependencies.map((_, NoData)).toMap)
        newDependants.values.foreach(_ ! ComponentUpdatedEvent(id, newState.derivedState))
        supervisor ! EventPropagated(newDependants.size)
        propagateStateIfDerivedStateChanged(state, newState)

      // updates component's check state
      case UpdateCheckState(checkStateId, checkStateState, timestamp) =>
        val newState = state.copy(checkStates = state.checkStates + (checkStateId -> checkStateState))
        propagateStateIfDerivedStateChanged(state, newState)

      // updates DerivedState of one of the dependency component
      case ComponentUpdatedEvent(componentId, dependencyDerivedState) =>
        val newState = state.copy(dependencyDerivedStates = state.dependencyDerivedStates + (componentId -> dependencyDerivedState))
        propagateStateIfDerivedStateChanged(state, newState)

      // sends current state snapshot
      case SentSnapshot(replyTo) =>
        replyTo ! (id -> ComponentData(state.ownState, state.derivedState, state.checkStates, state.dependencyDerivedStates.keySet, state.dependants.keySet))
        Behaviors.same
    }
  }
}
