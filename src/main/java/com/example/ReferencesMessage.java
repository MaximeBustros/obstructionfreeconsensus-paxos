package com.example;

import akka.actor.ActorRef;
import java.util.ArrayList;

public class ReferencesMessage {
	public ArrayList<ActorRef> references;
	
	public ReferencesMessage(ArrayList<ActorRef> references) {
		this.references = references;
	}
}