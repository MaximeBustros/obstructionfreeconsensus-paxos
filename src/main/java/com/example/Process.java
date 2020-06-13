package com.example;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.HashMap;
import java.util.ArrayList;
import java.lang.Math;

public class Process extends UntypedAbstractActor {
	
	// Enable Logging
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	
    // Process Properties
	private Integer ballot;
    private Integer proposal;
    private Integer readballot;
    private Integer imposeballot;
    private Integer estimate;
    private long time;
    private ArrayList<ActorRef> references;
    private int numberOfServers;
    private int quorumSize;
    private int numberOfGatherMessages;
    private HashMap<ActorRef, State> states; 	// State contains estimate and 
    											// estimateballot as public properties;
    
    // Constructor
    private Process(int ballot) {
    	this.ballot = ballot;
    	this.proposal = null;
    	this.readballot = 0;
    	this.imposeballot = ballot;
    	this.estimate = null;
    	this.states = null;
    	this.numberOfGatherMessages = 0;
    }
    
    // Method to create processes
    public static Props createActor(int ballot) {
        return Props.create(Process.class, () -> {
            return new Process(ballot);
        });
    }
    
    // Execute actions onReceive Messages of specific type
    @Override
    public void onReceive(Object message) throws Throwable {
    	if (message instanceof ReferencesMessage) {
    		addReferences(message);
    	} else if (message instanceof ProposeMessage) {
    		propose(message);
    	} else if (message instanceof ReadMessage) {
    		log.info("[" + self().path().name() + "] received READ from [" + getSender().path().name() + "]");
    		readMessage(message);
    	} else if (message instanceof AbortMessage) {
    		log.info("[" + self().path().name() + "] Aborts");
    	} else if (message instanceof GatherMessage) {
    		gather(message);
    	}
    }
    
    private void gather(Object message) {
    	GatherMessage gm = null;
    	log.info("[" + self().path().name() + "] Gather message received from [" + getSender() + "]");
    	try {
    		gm = (GatherMessage) message;
    	} catch (Exception e) {
    		log.error("Error in parsing GatherMessage");
    	}
    	states.put(getSender(), new State(gm.imposeballot, gm.estimate));
    	this.numberOfGatherMessages += 1;
    	if (this.numberOfGatherMessages >= this.quorumSize) {
    		// Do impose
    		log.info("[" + self().path().name() + "] quorum reached -> impose value: " + gm.ballot);
    		this.numberOfGatherMessages = 0;
    	}
    }
    
    private void readMessage(Object message) {
    	Integer messageballot = null;
    	try {
    		// parse message
    		ReadMessage m = (ReadMessage) message;
    		messageballot = m.ballot;
    	} catch (Exception e) {
    		log.error("Parsing Error: readMessage");
    		return;
    	}
    	if (this.readballot > messageballot || this.imposeballot > messageballot) {
    		AbortMessage abort = new AbortMessage(messageballot);
    		getSender().tell(abort, getSelf());
    	} else {
    		this.readballot = messageballot;
    		GatherMessage gather = new GatherMessage(this.readballot, this.imposeballot, estimate);
    		getSender().tell(gather, getSelf());
    	} 
    }
    
    private void propose(Object message) {
    	Integer value = null;
    	try {
    		// parse message
    		ProposeMessage m = (ProposeMessage) message;
    		value = m.value;
    		ReadMessage rm = new ReadMessage(value);
    		for (ActorRef actor : this.references) {
    			actor.tell(rm, self());
    		}
    	} catch (Exception e) {
    		log.error("ProposeError");
    		return;
    	}
    	
    }
    
    private void addReferences(Object message) {
    	ArrayList<ActorRef> refs = null;
    	try {
    		// parse message
    		ReferencesMessage m = (ReferencesMessage) message;
    		refs = m.references;
    	} catch (Exception e) {
    		log.error("Could not parse ReferencesMessage correctly");
    		return;
    	}
    	
    	// Copy references and get number of servers
    	this.references = refs;
    	this.numberOfServers = this.references.size();
    	this.quorumSize = Math.round((this.numberOfServers / 2 ) + 1) ;
    	
    	// if parsing was successful initialize states;
    	states = new HashMap<ActorRef, State>();
    	for (ActorRef act : this.references) {
    		states.put(act, new State(null, 0));
    	}
    	log.info(self().path().name() + "Correctly added states");
    }
    
    private void cleanStates() {
    	try {
    		states.replaceAll((key, oldValue) -> new State(null, 0)); 
        	log.info(self().path().name() + "States successfully cleaned");
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
