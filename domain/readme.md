## Into

There are multiple implementations we can go with

### 1. Fire and forget 
The assumption is that we allow events to be processed in parallel without any graph locking. 

Good for when we have a big graph of components and enormous number of events coming simultaneously and don't worry about intermediate results

We might enhance this by adding state versioning to keep track of graph version after applying each individual event

#### Implementation details
Every time we receive an event we send it to the actor component looking it up by ID. Actor component might send "state change" events to dependants.  

#####Pros 
Max performance (because of having less communications between actors). The implementation is fairly simple

#####Cons
Some intermediate results are not accurate (because graph paths of propagation of events might intersect), but eventually we end up with accurate result

Say component might receive Error state from one dependency earlier than Warning from another one even the Warning event is sent first, so if 
we trigger sending Error and Warning email reports then client may receive Error first and then Warning and ignore this thinking that
system has been recovered by its own. 

### 2. Processing events one a time
The idea is that we wait for the graph to stabilize before picking up the next event from the queue. 

Good for when we have non frequent events. Events are processed one after another and there won't be any intermediate incorrect states of the entire tree  

#### Implementation details
Every time we receive an event we send it to the actor component looking it up by ID. 

Supervisor actor registers every "state change" event sent to dependants and make sure all of them are done before moving to processing the next event
This might be done just maintainign a `var awaitingStateChanges: Int` on supervisor actor. Incrementing it after every state change event and decrementing on receiving event processed confirmation   

#####Pros 
Events are processed one after another and there won't be any intermediate incorrect states of the entire graph

#####Cons
Low performance (because of having having to wait till event propagates across the whole graph)

 
### 3. Selectively lock the sub graph 
The idea is that we calculate the possible path of propagation the event and lock that sub graph. 

Doing that we make it possible to process multiple events at a time if their paths are not intersect. 

That's a combination of first and second approach and lies somewhere between when we talk about performance and pros and cons  

#### Implementation details
Every time we receive an event we calculate the possible propagation path and send it to process if there is no locked components in that path   

#####Pros 
We still can track intermediate state before and after each event. The performance is fairly good until the whole graph elements depend on each other.
In that case the whole graph will be locked.   

#####Cons
Low performance for tight coupled graph, implementation is not so straightforward

### To sum it up

Taking into account that I have no specific restrictions I am implementing the simplest version that cover the needs described in assignment 