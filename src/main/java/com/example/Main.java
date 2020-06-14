package com.example;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.io.IOException;
import java.util.Random;
//import com.example.ProcessState;


public class Main {
	
	static final int NUMBER_OF_SERVERS = 10;
	static final int NUMBER_OF_FAILURES = 4;
	static final int TIMEOUT_DURATION = 500;
	static final int WAIT_DURATION = 60000;
	static final double CRASH_PROBABILITY = 0.10;
	
	private static ArrayList<ActorRef> createServers(ActorSystem system, int n) {
		ArrayList<ActorRef> references = new ArrayList<ActorRef>();
		for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
			Integer ballot = i - NUMBER_OF_SERVERS;
			final ActorRef a = system.actorOf(Process.createActor(ballot));
			references.add(a);
		}
		return references;
	}
	
	public static void main(String[] args) {
		final ActorSystem system = ActorSystem.create("system");
		
		ArrayList<ActorRef> references = createServers(system, NUMBER_OF_SERVERS);
		
		// Send to all actors references to each other
		for (ActorRef actor : references) {
			actor.tell(new ReferencesMessage(references), ActorRef.noSender());
		}
		
		try {
			Thread.sleep(2000);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//picking f random processes and sending them crash messages
        Collections.shuffle(references);
        CrashMessage cm = new CrashMessage(CRASH_PROBABILITY); // add crashprobability to crashmessage
        for (int i = 0; i < NUMBER_OF_FAILURES; i++ ) {
    		references.get(i).tell(cm, ActorRef.noSender());
        }

        // For every process, the main method then sends a special
        // launch message. Once process i receives a launch message, it picks 
        // an input value, randomly chosen in {0,1} and invokes instances of propose 
        // operation with this value until a value is decided.
        
        // create LaunchMessage
        LaunchMessage lm = new LaunchMessage();
        
        for (ActorRef a : references) {
        	a.tell(lm, ActorRef.noSender());
        }
        
        try {
        	Thread.sleep(TIMEOUT_DURATION);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        
        //picking a random leader
        int index = new Random().nextInt(references.size() - NUMBER_OF_FAILURES) + NUMBER_OF_FAILURES;

        ActorRef leader = references.get(index);
        System.out.println("LEADER IS :" + leader.path().name());
//        //send the others a hold message
        for (ActorRef actor : references) {
            if (!actor.equals(leader)) {
                actor.tell(new HoldMessage(), ActorRef.noSender());
            }
        }
        
		try {
			waitBeforeTerminate();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			system.terminate();
		}
	}

	public static void waitBeforeTerminate() throws InterruptedException {
		Thread.sleep(20000);
	}
}
