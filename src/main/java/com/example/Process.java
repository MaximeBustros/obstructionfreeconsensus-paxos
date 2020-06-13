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
    private ArrayList<ActorRef> references;
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
    	
    	this.references = refs;
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
