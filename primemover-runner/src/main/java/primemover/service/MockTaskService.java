package primemover.service;

import java.util.ArrayList;
import java.util.List;

import primemover.model.Agent;

public class MockTaskService implements ITaskService {

	@Override
	public List<Agent> getAgents() {
		ArrayList<Agent> res = new ArrayList<Agent>();
		
		Agent agnt = new Agent();
		agnt.setId(10);
		agnt.setName("SuperCarefull");
		agnt.setDescription("This is a super carefull user");
		res.add(agnt);
		
		agnt = new Agent();
		agnt.setId(11);
		agnt.setName("FuckOff");
		agnt.setDescription("Everything doesn't matter");
		res.add(agnt);
		
		return res;
	}

}
