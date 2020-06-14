package com.example;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.lang.Math;
import java.util.Random;
//import com.example.ProcessState;

public class Process extends UntypedAbstractActor {
	// Enable Logging
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    
    // Process States
    public ProcessState processState;
    public boolean isHoldOn; 
    
    // proposal Launcher
    private Launcher launcher;
    
    // Process Properties
    private double crashProbability; // only for faultprone
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
    private int numberOfAcknowledgementMessage;
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
    	this.numberOfAcknowledgementMessage = 0;
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
    	// if not SILENT then process messages
    	if (this.processState != ProcessState.SILENT) {
    		// If processState is faultprone then try to crash with crashProbability
    		if (this.processState == ProcessState.FAULTPRONE) {
    			if (tryCrash())
    				log.info("Process [" + self().path().name() + "] crashed");
    				return;
    		}
    		
	    	if (message instanceof ReferencesMessage) {
	    		// Adds References to all processes and Initializes States
	    		addReferences(message);
	    	} else if (message instanceof CrashMessage) {
	    		// if receive crash message go into FAULTPRONEMODE
	    		this.processState = ProcessState.FAULTPRONE;
	    		log.info("Process [" + self().path().name() + "] is now in FAULTPRONE Mode");
	    	} else if (message instanceof LaunchMessage) {
                launcher = new Launcher(new Random().nextInt(2));
                launcher.start();
	    	}else if (message instanceof HoldMessage) {
	    		this.isHoldOn = true;
	    	} else if (message instanceof ProposeMessage) {
	    		// Force a process to begin proposing
	    		this.processState = ProcessState.PROPOSING;
	    		propose(message);
	    	} else if (message instanceof ReadMessage) {
//	    		log.info("[" + self().path().name() + "] received READ from [" + getSender().path().name() + "]");
	    		readMessage(message);
	    	} else if (message instanceof AbortMessage) {
	    		if (this.processState != ProcessState.DECIDED && this.processState != ProcessState.ABORT && !this.isHoldOn) {
//		    		log.info("[" + self().path().name() + "] Aborts");
		    		
		    		// When a proposing process receives an abort stop treating subsequent Abort messages;
		    		this.processState = ProcessState.ABORT;
		    		// if a thread receives an abort message then make a new proposal
		    		try {
		    			launcher.notify();
		    		} catch (Exception e) {
		    		}
	    		}
	    	} else if (message instanceof GatherMessage) {
	    		if (this.processState == ProcessState.PROPOSING) {
	    			if (gather(message)) {
	    				// if gather message executed correctly then count +1
	    				this.numberOfGatherMessages += 1;
	    			}
	    			// Check if quorum is achieved
	    			if (this.numberOfGatherMessages >= this.quorumSize) {
	    				// Change state to impose
	    				this.processState = ProcessState.IMPOSING;
	    				this.numberOfGatherMessages = 0;
	    				
	    				// get actor with highest estimate ballot if > 0
	    				if (existsActorWithPositiveEstimateBallot()) {
	    					ActorRef a = getActorWithHighestEstimateBallot();
	    					// get estimate
		    				this.proposal = this.states.get(a).estimate;
	    				}

	    				// reset states to [null, 0]^n
	    				cleanStates();
	    				
	    				// Send impose to all
	    				ImposeMessage im = new ImposeMessage(this.ballot, this.proposal);
	    				for (ActorRef actor : this.references) {
	    					actor.tell(im, self());
	    				}
	    			}
	    		}
	    	} else if (message instanceof ImposeMessage) {
	    		impose(message);
	    	} else if (message instanceof AcknowledgementMessage) {
	    		if (this.processState == ProcessState.IMPOSING && !this.isHoldOn) {
	    			if (acknowledge(message)) { // if successfully processed acknolegement
	    		    	this.numberOfAcknowledgementMessage += 1; // count
	    		    	
	    		    	// check if quorum is achieved
	    		    	if (this.numberOfAcknowledgementMessage >= this.quorumSize) {
	    		    		// change state to decided
	    		    		this.processState = ProcessState.DECIDED;
	    		    		this.numberOfAcknowledgementMessage = 0;
	    		    		
		    		    	// send decide message to all
	    		    		DecideMessage dm = new DecideMessage(this.proposal);
	    		    		for (ActorRef actor : this.references) {
	    		    			actor.tell(dm, self());
	    		    		}
	    		    		this.processState = ProcessState.DECIDED;
	    		    		// once leader has decided stop proposing;
//	    		    		this.isHoldOn = true;
	    		    		log.info("Leader [" + self().path().name() + "] has decided on: " + this.proposal);
	    		    		try {
	    		    			launcher.notify();
	    		    		} catch (Exception e) {
	    		    			
	    		    		}
	    		    	}
	    			}
	    		}
	    	} else if (message instanceof DecideMessage) {
	    		if (this.processState != ProcessState.DECIDED) {
	    			this.processState = ProcessState.DECIDED;
		    		decide(message);
	    		}
	    	}
    	}
    }
    
    private void receiveCrashMessage(Object message) {
		CrashMessage cm = null;
		try {
			cm = (CrashMessage) message;
			this.crashProbability = cm.crashProbability;
		} catch (Exception e) {
			log.error("Parsing error of CrashMessage");
		}
    	this.processState = ProcessState.FAULTPRONE;
    }
    
    private boolean tryCrash() {
    	if (Math.random() < this.crashProbability) {
    		this.processState = ProcessState.SILENT;
    		return true;
    	}
    	return false;
    }

    
    private void decide(Object message) {
    	// Parse message
    	DecideMessage dm = null;
    	try {
    		dm = (DecideMessage) message;
    	} catch (Exception e) {
    		log.error("Error in parsing DecideMessage");
    		return;
    	}
    	
    	// Send decideMessage to all;
		for (ActorRef actor : this.references) {
			actor.tell(dm, self());
		}
//		// log decided value and return;
//    	log.info("[" + self().path().name() + "] has decided on value: " + dm.proposal);
//    	return;
    }
    
    private boolean acknowledge(Object message) {
    	// Parse message
    	AcknowledgementMessage am = null;
    	try {
    		am = (AcknowledgementMessage) message;
    	} catch (Exception e) {
    		log.error("Error in parsing AcknowledgementMessage");
    		return false;
    	}
    	return true;
    }
    
    private void impose(Object message) {
    	// Parse message
    	ImposeMessage im = null;
//    	log.info("[" + self().path().name() + "] Impose message received from [" + getSender().path().name() + "]");
    	try {
    		im = (ImposeMessage) message;
    	} catch (Exception e) {
    		log.error("Error in parsing ImposeMessage");
    		return;
    	}
    	
//    	log.info("readballot = " + this.readballot);
//    	log.info("im.ballot = " + im.ballot);
//    	log.info("imposeballot = " + this.imposeballot);
    	
    	if (this.readballot > im.ballot || this.imposeballot > im.ballot) {
    		AbortMessage ab = new AbortMessage(im.ballot);
    		getSender().tell(ab, self());
    	} else {
    		this.estimate = im.proposal;
    		this.imposeballot = im.ballot;
    		AcknowledgementMessage am = new AcknowledgementMessage(ballot);
    		getSender().tell(am, self());
    	}
    }
    
    private boolean gather(Object message) {
    	GatherMessage gm = null;
    	try {
    		gm = (GatherMessage) message;
    	} catch (Exception e) {
    		log.error("Error in parsing GatherMessage");
    		return false;
    	}
//    	log.info("[" + self().path().name() + "] Gather message received from [" + getSender() + "]");
    	if (gm.ballot == this.readballot) { // if gather is a response to the actual proposed value
	    	states.put(getSender(), new State(gm.imposeballot, gm.estimate));
	    	return true;
    	}
    	return false;
    }
    
    private boolean existsActorWithPositiveEstimateBallot() {
    	for (Map.Entry<ActorRef, State> entry : this.states.entrySet()) {
		    ActorRef key = (ActorRef) entry.getKey();
		    State value = (State) entry.getValue();
		    try {
			    if (value.estimateBallot > 0) {
			    	return true;
			    }
		    } catch (NullPointerException e) {
		    	// if null pointer just keep going
		    }
		}
    	return false;
    }
    
    private ActorRef getActorWithHighestEstimateBallot() {
    	ActorRef max = self();
		Integer maxEst = this.states.get(self()).estimateBallot;
		// select states[pk]=[est,estballot] > 0 with highest estballot 
		for (Map.Entry<ActorRef, State> entry : this.states.entrySet()) {
		    ActorRef key = (ActorRef) entry.getKey();
		    State value = (State) entry.getValue();
		    try {
			    if (value.estimateBallot > maxEst) {
			    	maxEst = value.estimateBallot;
			    	max = key;
			    }
		    } catch (NullPointerException e) {
		    	// if null pointer just keep going
		    }
		}
		return max;
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
    	ProposeMessage m = null;
    	try {
    		// parse message
    		m = (ProposeMessage) message;
    	} catch (Exception e) {
    		log.error("ProposeError");
    	}
		this.proposal = m.value;
		this.ballot = this.ballot + this.numberOfServers;
		cleanStates();
		
		ReadMessage rm = new ReadMessage(this.ballot);
		for (ActorRef actor : this.references) {
			actor.tell(rm, self());
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
//        	log.info(self().path().name() + "States successfully cleaned");
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
    
    class Launcher extends Thread {
        private int proposal;

        public Launcher(int proposal) {
            this.proposal = proposal;
            this.setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            while (true) {
            	if (processState != ProcessState.DECIDED && !isHoldOn && processState != ProcessState.SILENT) {
//                	Try proposing
                    self().tell(new ProposeMessage(proposal), ActorRef.noSender());
            	}
            	// After sending a proposal or not wait
            	// if a process receives an abort message then notify
            	try {
            		wait();
            	} catch (Exception e) {
//            		e.printStackTrace();
            	}
            }
        }

    }
}
    

