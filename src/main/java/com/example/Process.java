package com.example;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.HashMap;
import java.util.ArrayList;

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
    private HashMap<ActorRef, State> states; 	// State contains estimate and 
    											// estimateballot as public properties;
    
    // Constructor
    private Process(int ballot) {
    	this.ballot = ballot;
    	this.proposal = null;
    	this.readballot = 0;
    	this.imposeballot = ballot;
    	this.estimate = null;
//    	this.states = ;
//    	
//    	log.info
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
    	// if parsing was successful
    	states = new HashMap<ActorRef, State>();
    	State s = new State(null, 0);
    	for (ActorRef act : refs) {
    		states.put(act, s);
    	}
    	log.info(self().path().name() + "Correctly added states");
    }
}