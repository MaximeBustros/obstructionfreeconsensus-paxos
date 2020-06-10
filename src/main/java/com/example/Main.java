package com.example;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import java.util.ArrayList;

import java.io.IOException;
public class Main {
	
	static final int NUMBER_OF_SERVERS = 20;
	static final int NUMBER_OF_FAILURES = 9;
	static final int TIMEOUT_DURATION = 50;
	static final int WAIT_DURATION = 10000;
	
	private static ArrayList<ActorRef> createServers(ActorSystem system, int n) {
		ArrayList<ActorRef> references = new ArrayList<ActorRef>();
		for (int i = 0; i < NUMBER_OF_SERVERS; i++) {
			final ActorRef a = system.actorOf(Process.createActor());
			references.add(a);
		}
		return references;
	}
	
	public static void main(String[] args) {
		final ActorSystem system = ActorSystem.create("system");
		
		ArrayList<ActorRef> references = createServers(system, NUMBER_OF_SERVERS);
		
	}
}
