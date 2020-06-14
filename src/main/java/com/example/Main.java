package com.example;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.io.IOException;


public class Main {
	
	static final int NUMBER_OF_SERVERS = 20;
	static final int NUMBER_OF_FAILURES = 9;
	static final int TIMEOUT_DURATION = 50;
	static final int WAIT_DURATION = 10000;
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
        	ActorRef a = references.get(i);
        	if (a != references.get(0)) {
        		a.tell(cm, ActorRef.noSender()); // prevent process 1 from being fault prone;
        	}
        }

		ProposeMessage p = new ProposeMessage(10);
		references.get(0).tell(p, ActorRef.noSender());
		
		
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
